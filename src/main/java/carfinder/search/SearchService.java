package carfinder.search;

import carfinder.data.DataStore;
import carfinder.geo.Places;
import carfinder.knowledge.Generation;
import carfinder.knowledge.KnowledgeBase;
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
import carfinder.source.marketplace.LoginWallException;
import carfinder.source.marketplace.SoldOrReservedException;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
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
	static final String BUSY = "Já há uma pesquisa a decorrer";
	static final String STALE = "A aplicação foi fechada durante a pesquisa";
	private static final Duration POOL_AGE = Duration.ofDays(30);
	private static final Duration DETAILS_AGE = Duration.ofDays(7);
	/** Login walls in a row after which a run stops opening that site's ad pages (no retries). */
	static final int WALL_LIMIT = 3;
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
	private final KnowledgeBase kb;
	private final Executor executor;
	private final Duration needsUserTimeout;
	private final Duration needsUserRetry;
	private final AtomicBoolean busy = new AtomicBoolean();
	private final DataStore data;

	@Autowired
	public SearchService(List<ListingSource> sources, ListingStore store, ListingRepository listings,
			SearchRunRepository runs, ScoringService scoring, JsonMapper json, Clock clock,
			@Value("${carfinder.needs-user-timeout}") Duration needsUserTimeout,
			@Value("${carfinder.needs-user-retry}") Duration needsUserRetry, Places places, KnowledgeBase kb,
			DataStore data) {
		this(sources, store, listings, runs, scoring, json, clock, Executors.newSingleThreadExecutor(),
				needsUserTimeout, needsUserRetry, places, kb, data);
	}

	SearchService(List<ListingSource> sources, ListingStore store, ListingRepository listings,
			SearchRunRepository runs, ScoringService scoring, JsonMapper json, Clock clock, Executor executor,
			Duration needsUserTimeout, Duration needsUserRetry, Places places, KnowledgeBase kb, DataStore data) {
		this.data = data;
		this.places = places;
		this.kb = kb;
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
		return start(limits, preset, custom, sites, false);
	}

	/** {@code automated}: Marketplace also searches the site itself (secondary account), not only the imports. */
	public UUID start(SearchLimits limits, Preset preset, Weights custom, Set<Site> sites, boolean automated) {
		preset.weights(custom); // IllegalArgumentException for bad custom weights
		if (sites.isEmpty()) {
			throw new IllegalArgumentException("Escolhe pelo menos um site");
		}
		// Marketplace ranks the imported ads, so it needs no source bean
		if (!sites.stream().allMatch(s -> s == Site.MARKETPLACE || sources.containsKey(s))) {
			throw new IllegalArgumentException(UNSUPPORTED);
		}
		if (limits.centre() == null || limits.centre().isBlank() || limits.radiusKm() == null
				|| limits.radiusKm() <= 0) {
			throw new IllegalArgumentException("Indica o centro e o raio da pesquisa");
		}
		places.centre(limits.centre()); // unknown or ambiguous centre fails before the run starts
		Generation typed = typed(limits);
		limits = narrowed(limits, typed);
		if (!busy.compareAndSet(false, true)) {
			throw new IllegalStateException(BUSY);
		}
		try {
			UUID id = UUID.randomUUID();
			runs.save(new SearchRun(id, json.writeValueAsString(limits), preset,
					custom == null ? null : json.writeValueAsString(custom), sites, clock.instant()));
			SearchLimits l = limits;
			Generation g = typed;
			executor.execute(() -> run(id, l, sites, g, automated));
			return id;
		}
		catch (RuntimeException e) {
			busy.set(false);
			throw e;
		}
	}

	/** Runs work (an import) in the same slot as a search, so the two never overlap. */
	public <T> T whileIdle(Supplier<T> work) {
		if (!busy.compareAndSet(false, true)) {
			throw new IllegalStateException(BUSY);
		}
		try {
			return work.get();
		}
		finally {
			busy.set(false);
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
		SearchLimits caps = limits == null ? NO_LIMITS : narrowed(limits, typed(limits));
		Preset p = preset == null ? Preset.BALANCED : preset;
		p.weights(custom);
		RawListing fetched = source.details(url);
		RawListing raw = listings.findBySiteAndExternalId(site, fetched.externalId()).map(l -> gapsOnly(l, fetched))
				.orElse(fetched);
		Listing stored = store.applyDetails(store.upsert(raw), raw);
		return scoring.rank(List.of(stored), pool(), caps, p, custom);
	}

	/** The typed make and model, or null when none was typed; unknown text fails with a suggestion. */
	private Generation typed(SearchLimits limits) {
		String text = limits.makeModel();
		return text == null ? null : kb.recogniseTyped(text).orElseThrow(
				() -> new UnknownMakeModelException(text, kb.closestMakeModel(text).orElse(null)));
	}

	/** The typed model replaces the shortlist / every B/C model (Marco, 2026-11-09). */
	private static SearchLimits narrowed(SearchLimits limits, Generation typed) {
		if (typed == null) {
			return limits;
		}
		String name = typed.make() + " " + typed.model();
		return new SearchLimits(limits.maxPriceEur(), limits.priceCap(), limits.maxKm(), limits.minYear(),
				limits.fuels(), limits.centre(), limits.radiusKm(), List.of(name), limits.realCost(), limits.minDoors(),
				limits.showDamaged(), name);
	}

	/**
	 * Marketplace pages give year and km only from the seller's free text ("revisão aos 500 km"), so the imported
	 * figures (Facebook's own fields) win; the page only fills what the import lacked.
	 */
	private static RawListing gapsOnly(Listing l, RawListing d) {
		if (d.site() != Site.MARKETPLACE) {
			return d;
		}
		return new RawListing(d.site(), d.externalId(), d.url(), d.title(), d.priceEur(), d.make(), d.model(),
				d.version(), l.getYear() != null ? null : d.year(), d.month(), l.getKm() != null ? null : d.km(),
				d.fuel(), d.cc(), d.powerCv(), d.gearbox(), d.doors(), d.sellerType(), d.district(), d.city(),
				d.photoCount(), d.description(), d.imported(), d.parts());
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

	private void run(UUID id, SearchLimits limits, Set<Site> sites, Generation typed, boolean automated) {
		try {
			SearchQuery query = new SearchQuery(limits.maxPriceEur(), limits.maxKm(), limits.minYear(),
					typed == null ? null : typed.make(), typed == null ? null : typed.model(), automated);
			Preset anyPreset = Preset.BALANCED; // the filter does not depend on weights
			int attempted = 0;
			int failed = 0;
			int walled = 0;
			Site failedSite = null;
			Site backedOff = null;
			int skipped = 0; // left unread after a back-off; not part of the broken-parser rule
			for (Site site : sites) {
				ListingSource source = sources.get(site);
				if (site == Site.MARKETPLACE && automated && source != null) {
					// what the automated search found joins the imported ads as one import batch
					List<RawListing> found = waitingForUser(id,
							() -> source.search(query, msg -> change(id, r -> r.update(RunStatus.RUNNING, msg))));
					int added = (int) found.stream()
							.filter(f -> listings.findBySiteAndExternalId(site, f.externalId()).isEmpty()).count();
					data.recordImport(site, found.size(), added, found.stream().map(f -> store.upsert(f).getId()).toList());
				}
				// Marketplace: every imported ad, however old (a HAR cannot tell when an ad was removed)
				List<Listing> saved = site == Site.MARKETPLACE ? listings.findBySite(site)
						: waitingForUser(id,
								() -> source.search(query, msg -> change(id, r -> r.update(RunStatus.RUNNING, msg))))
								.stream().map(store::upsert).toList();
				change(id, r -> r.addListings(saved.stream().map(Listing::getId).toList()));
				if (source == null) {
					continue; // advert pages need a source for the site
				}

				Set<Listing> dropped = Collections.newSetFromMap(new IdentityHashMap<>());
				ScoringService.Ranking pre = scoring.rank(saved, pool(), limits, anyPreset, null);
				pre.excluded().stream().map(Excluded::listing).forEach(dropped::add);
				// best pre-filter rank first, so a page budget is spent on the likeliest ads; duplicates go last
				Map<Listing, Integer> order = new IdentityHashMap<>();
				for (int i = 0; i < pre.ranked().size(); i++) {
					order.put(pre.ranked().get(i).listing(), i);
				}
				Instant stale = clock.instant().minus(DETAILS_AGE);
				List<Listing> toRead = saved.stream().filter(l -> !dropped.contains(l))
						.filter(l -> l.getDetailFetchedAt() == null || l.getDetailFetchedAt().isBefore(stale))
						.sorted(Comparator.comparingInt(l -> order.getOrDefault(l, Integer.MAX_VALUE)))
						.limit(source.maxDetailsPerRun()).toList();
				int wallsInARow = 0;
				for (int i = 0; i < toRead.size(); i++) {
					if (wallsInARow == WALL_LIMIT) {
						skipped += toRead.size() - i; // the rest stay unread
						backedOff = site;
						break;
					}
					Listing l = toRead.get(i);
					String msg = site + ": a ler anúncio " + (i + 1) + " de " + toRead.size();
					change(id, r -> r.update(RunStatus.RUNNING, msg));
					attempted++;
					try {
						RawListing details = waitingForUser(id, () -> source.details(l.getUrl()));
						store.applyDetails(l, gapsOnly(l, details));
						wallsInARow = 0;
					}
					catch (SoldOrReservedException e) {
						store.markUnavailable(site, l.getExternalId()); // excluded "vendido ou reservado" from now on
						wallsInARow = 0;
					}
					catch (LoginWallException e) {
						// the page needs a login: the ad keeps its imported data; says nothing about the parser
						walled++;
						wallsInARow++;
					}
					catch (SourceException e) {
						// a removed ad (404) keeps its search-page data
						log.info("Advert page failed for {}: {}", l.getUrl(), e.getMessage());
						wallsInARow = 0;
						failed++;
						failedSite = site;
					}
				}
			}
			// Every advert page failing is a broken parser, not a few removed ads.
			if (attempted - walled >= 3 && failed == attempted - walled) {
				throw new SourceException(failedSite.formatChangedPt());
			}
			int collected = runs.findById(id).orElseThrow().getCollected();
			int unread = failed + walled + skipped;
			String done = "Pesquisa concluída: " + collected + (collected == 1 ? " anúncio recolhido." : " anúncios recolhidos.")
					+ (unread == 0 ? "" : " Não foi possível ler " + unread + (unread == 1 ? " anúncio" : " anúncios")
							+ " em detalhe; ficam com os dados da pesquisa.")
					+ (backedOff == null ? "" : " O " + backedOff.label + " pediu login " + WALL_LIMIT
							+ " vezes seguidas; parei de abrir anúncios desse site nesta pesquisa.");
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
