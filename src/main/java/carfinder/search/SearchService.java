package carfinder.search;

import carfinder.geo.Places;
import carfinder.listing.Fuel;
import carfinder.listing.Listing;
import carfinder.listing.ListingRepository;
import carfinder.listing.ListingStore;
import carfinder.listing.RawListing;
import carfinder.listing.Site;
import carfinder.scoring.Excluded;
import carfinder.scoring.Preset;
import carfinder.scoring.ScoringService;
import carfinder.scoring.Weights;
import carfinder.source.ListingSource;
import carfinder.source.NeedsUserException;
import carfinder.source.SearchQuery;
import carfinder.source.SourceException;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs one search at a time on a background thread: search each source, store the ads, pre-filter on search-page
 * data, then read the advert pages of the survivors. Results are scored when read.
 */
@Service
public class SearchService {

	static final String UNSUPPORTED = "Site ainda não suportado";
	static final String TIMEOUT = "Tempo esgotado à espera do login/verificação";
	static final String STALE = "A aplicação foi fechada durante a pesquisa";
	private static final Duration POOL_AGE = Duration.ofDays(30);
	private static final Duration DETAILS_AGE = Duration.ofDays(7);
	/** Pasted links are scored without the user's caps; only the ad's own problems exclude it. */
	private static final SearchLimits NO_LIMITS = new SearchLimits(Integer.MAX_VALUE, null, Integer.MAX_VALUE, 1,
			EnumSet.allOf(Fuel.class), null, null, null, null, null, null);
	private static final Logger log = LoggerFactory.getLogger(SearchService.class);

	private final Map<Site, ListingSource> sources;
	private final ListingStore store;
	private final ListingRepository listings;
	private final SearchRunRepository runs;
	private final ScoringService scoring;
	private final JsonMapper json;
	private final Clock clock;
	private final Places places;
	private final Executor executor;
	private final Duration needsUserTimeout;
	private final Duration needsUserRetry;
	private final AtomicBoolean busy = new AtomicBoolean();

	@Autowired
	public SearchService(List<ListingSource> sources, ListingStore store, ListingRepository listings,
			SearchRunRepository runs, ScoringService scoring, JsonMapper json, Clock clock,
			@Value("${carfinder.needs-user-timeout}") Duration needsUserTimeout,
			@Value("${carfinder.needs-user-retry}") Duration needsUserRetry, Places places) {
		this(sources, store, listings, runs, scoring, json, clock, Executors.newSingleThreadExecutor(),
				needsUserTimeout, needsUserRetry, places);
	}

	SearchService(List<ListingSource> sources, ListingStore store, ListingRepository listings,
			SearchRunRepository runs, ScoringService scoring, JsonMapper json, Clock clock, Executor executor,
			Duration needsUserTimeout, Duration needsUserRetry, Places places) {
		this.places = places;
		this.sources = sources.stream().collect(Collectors.toMap(ListingSource::site, Function.identity()));
		this.store = store;
		this.listings = listings;
		this.runs = runs;
		this.scoring = scoring;
		this.json = json;
		this.clock = clock;
		this.executor = executor;
		this.needsUserTimeout = needsUserTimeout;
		this.needsUserRetry = needsUserRetry;
	}

	/** Thrown when the user did not finish the login/check in time; fails the run, never skips an ad. */
	private static final class NeedsUserTimeout extends RuntimeException {
	}

	public UUID start(SearchLimits limits, Preset preset, Weights custom, Set<Site> sites) {
		preset.weights(custom); // IllegalArgumentException for bad custom weights
		if (sites.isEmpty()) {
			throw new IllegalArgumentException("Escolhe pelo menos um site");
		}
		if (!sources.keySet().containsAll(sites)) {
			throw new IllegalArgumentException(UNSUPPORTED);
		}
		if (limits.centre() == null || limits.centre().isBlank() || limits.radiusKm() == null
				|| limits.radiusKm() <= 0) {
			throw new IllegalArgumentException("Indica o centro e o raio da pesquisa");
		}
		places.centre(limits.centre()); // unknown or ambiguous centre fails before the run starts
		if (!busy.compareAndSet(false, true)) {
			throw new IllegalStateException("Já há uma pesquisa a decorrer");
		}
		try {
			UUID id = UUID.randomUUID();
			runs.save(new SearchRun(id, json.writeValueAsString(limits), preset,
					custom == null ? null : json.writeValueAsString(custom), sites, clock.instant()));
			executor.execute(() -> run(id, limits, sites));
			return id;
		}
		catch (RuntimeException e) {
			busy.set(false);
			throw e;
		}
	}

	public RunView status(UUID id) {
		SearchRun r = runs.findById(id).orElseThrow();
		return new RunView(r.getId(), r.getStatus(), r.getMessagePt(), r.getCollected());
	}

	public ScoringService.Ranking results(UUID id) {
		SearchRun r = runs.findById(id).orElseThrow();
		SearchLimits limits = json.readValue(r.getLimitsJson(), SearchLimits.class);
		Weights custom = r.getCustomWeightsJson() == null ? null
				: json.readValue(r.getCustomWeightsJson(), Weights.class);
		return scoring.rank(listings.findAllById(r.getListingIds()), pool(), limits, r.getPreset(), custom);
	}

	/** Reads one advert page and ranks that ad alone against the pool; null limits = none of the user's caps. */
	public ScoringService.Ranking scoreLink(Site site, String url, SearchLimits limits, Preset preset,
			Weights custom) {
		ListingSource source = sources.get(site);
		if (source == null) {
			throw new IllegalArgumentException(UNSUPPORTED);
		}
		Preset p = preset == null ? Preset.BALANCED : preset;
		p.weights(custom);
		RawListing raw = source.details(url);
		Listing l = store.applyDetails(store.upsert(raw), raw);
		return scoring.rank(List.of(l), pool(), limits == null ? NO_LIMITS : limits, p, custom);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void failStaleRuns() {
		for (SearchRun r : runs.findByStatusIn(List.of(RunStatus.RUNNING, RunStatus.NEEDS_USER))) {
			r.finish(RunStatus.FAILED, STALE, clock.instant());
			runs.save(r);
		}
	}

	@PreDestroy
	void stop() {
		if (executor instanceof ExecutorService es) {
			es.shutdownNow();
		}
	}

	private void run(UUID id, SearchLimits limits, Set<Site> sites) {
		try {
			SearchQuery query = new SearchQuery(limits.maxPriceEur(), limits.maxKm(), limits.minYear());
			Preset anyPreset = Preset.BALANCED; // the filter does not depend on weights
			int attempted = 0;
			int failed = 0;
			Site failedSite = null;
			for (Site site : sites) {
				ListingSource source = sources.get(site);
				List<RawListing> found = waitingForUser(id,
						() -> source.search(query, msg -> change(id, r -> r.update(RunStatus.RUNNING, msg))));
				List<Listing> saved = found.stream().map(store::upsert).toList();
				change(id, r -> r.addListings(saved.stream().map(Listing::getId).toList()));

				Set<Listing> dropped = Collections.newSetFromMap(new IdentityHashMap<>());
				scoring.rank(saved, pool(), limits, anyPreset, null).excluded().stream().map(Excluded::listing)
						.forEach(dropped::add);
				Instant stale = clock.instant().minus(DETAILS_AGE);
				List<Listing> toRead = saved.stream().filter(l -> !dropped.contains(l))
						.filter(l -> l.getDetailFetchedAt() == null || l.getDetailFetchedAt().isBefore(stale))
						.toList();
				for (int i = 0; i < toRead.size(); i++) {
					Listing l = toRead.get(i);
					String msg = site + ": a ler anúncio " + (i + 1) + " de " + toRead.size();
					change(id, r -> r.update(RunStatus.RUNNING, msg));
					attempted++;
					try {
						RawListing details = waitingForUser(id, () -> source.details(l.getUrl()));
						store.applyDetails(l, details);
					}
					catch (SourceException e) {
						// a removed ad (404) keeps its search-page data
						log.info("Advert page failed for {}: {}", l.getUrl(), e.getMessage());
						failed++;
						failedSite = site;
					}
				}
			}
			// Every advert page failing is a broken parser, not a few removed ads.
			if (attempted >= 3 && failed == attempted) {
				throw new SourceException(failedSite.formatChangedPt());
			}
			int collected = runs.findById(id).orElseThrow().getCollected();
			String done = "Pesquisa concluída: " + collected + (collected == 1 ? " anúncio recolhido." : " anúncios recolhidos.")
					+ (failed == 0 ? "" : " Não foi possível ler " + failed + (failed == 1 ? " anúncio" : " anúncios")
							+ " em detalhe; ficam com os dados da pesquisa.");
			finish(id, RunStatus.DONE, done);
		}
		catch (SourceException e) {
			finish(id, RunStatus.FAILED, e.getMessage());
		}
		catch (NeedsUserTimeout e) {
			finish(id, RunStatus.FAILED, TIMEOUT);
		}
		catch (RuntimeException e) {
			log.error("Search run {} failed", id, e);
			finish(id, RunStatus.FAILED, "Erro inesperado na pesquisa; os detalhes estão no registo da aplicação");
		}
		finally {
			busy.set(false);
		}
	}

	/** Retries every needsUserRetry while the source needs the user, up to needsUserTimeout. */
	private <T> T waitingForUser(UUID id, Supplier<T> call) {
		long deadline = 0;
		boolean waiting = false;
		while (true) {
			try {
				T result = call.get();
				if (waiting) {
					change(id, r -> r.update(RunStatus.RUNNING, "A continuar a pesquisa"));
				}
				return result;
			}
			catch (NeedsUserException e) {
				if (!waiting) {
					waiting = true;
					deadline = System.nanoTime() + needsUserTimeout.toNanos();
				}
				if (System.nanoTime() - deadline >= 0) {
					throw new NeedsUserTimeout();
				}
				change(id, r -> r.update(RunStatus.NEEDS_USER, e.getMessage()));
				try {
					Thread.sleep(needsUserRetry);
				}
				catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					throw new SourceException("Pesquisa interrompida", ie);
				}
			}
		}
	}

	private List<Listing> pool() {
		return listings.findByLastSeenAfter(clock.instant().minus(POOL_AGE));
	}

	private void change(UUID id, Consumer<SearchRun> c) {
		SearchRun r = runs.findById(id).orElseThrow();
		c.accept(r);
		runs.save(r);
	}

	private void finish(UUID id, RunStatus status, String messagePt) {
		change(id, r -> r.finish(status, messagePt, clock.instant()));
	}
}
