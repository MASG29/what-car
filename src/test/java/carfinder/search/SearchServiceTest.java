package carfinder.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import carfinder.TestcontainersConfiguration;
import carfinder.buying.ChecklistBuilder;
import carfinder.buying.RealCostCalculator;
import carfinder.geo.Places;
import carfinder.knowledge.KnowledgeBase;
import carfinder.knowledge.KnowledgeLoader;
import carfinder.listing.Fuel;
import carfinder.listing.Listing;
import carfinder.listing.ListingRepository;
import carfinder.listing.ListingStore;
import carfinder.listing.RawListing;
import carfinder.listing.Site;
import carfinder.matching.CarMatcher;
import carfinder.scoring.CompletenessScorer;
import carfinder.scoring.ConsumptionScorer;
import carfinder.scoring.PhotoScorer;
import carfinder.scoring.Preset;
import carfinder.scoring.PriceProperties;
import carfinder.scoring.PriceScorer;
import carfinder.scoring.ReliabilityScorer;
import carfinder.scoring.ScoredListing;
import carfinder.scoring.ScoringService;
import carfinder.search.SearchLimits.PriceCap;
import carfinder.source.ListingSource;
import carfinder.source.NeedsUserException;
import carfinder.source.SearchQuery;
import carfinder.source.SourceException;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest
@Import({TestcontainersConfiguration.class, ListingStore.class})
// The run executes on another thread in one test, so writes must commit.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SearchServiceTest {

	private static final KnowledgeBase KB = KnowledgeLoader
			.load(Paths.get("src/test/resources/fixtures/knowledge-reliability"));
	private static final SearchLimits LIMITS = new SearchLimits(5000, PriceCap.ASKING, 300000, 1995,
			EnumSet.allOf(Fuel.class), "Ponte de Lima", 500, List.of(), null, null, null);
	private static final Places PLACES = new Places(Paths.get("src/test/resources/fixtures/geo/places-test.tsv"));
	private static final Duration LONG = Duration.ofMinutes(10);

	@Autowired
	ListingStore store;
	@Autowired
	ListingRepository listings;
	@Autowired
	SearchRunRepository runs;

	private final ScoringService scoring = new ScoringService(new CarMatcher(KB), new ReliabilityScorer(KB),
			new PriceScorer(new PriceProperties(1.25, 0.80)), new ConsumptionScorer(KB), new CompletenessScorer(),
			new PhotoScorer(), new RealCostCalculator(), new ChecklistBuilder(), new ListingFilter(KB, PLACES),
			Clock.systemUTC());
	private final ListingSource source = mock(ListingSource.class);

	@BeforeEach
	void clean() {
		runs.deleteAll();
		listings.deleteAll();
		when(source.site()).thenReturn(Site.STANDVIRTUAL);
		when(source.maxDetailsPerRun()).thenReturn(Integer.MAX_VALUE); // a mock skips the interface default
	}

	@org.junit.jupiter.api.AfterEach
	void cleanUp() {
		runs.deleteAll(); // writes commit here; other test classes share the database
		listings.deleteAll();
	}

	private SearchService service(java.util.concurrent.Executor executor, Duration timeout) {
		return new SearchService(List.of(source), store, listings, runs, scoring, JsonMapper.builder().build(),
				Clock.systemUTC(), executor, timeout, Duration.ofMillis(10), PLACES);
	}

	private SearchService service() {
		return service(Runnable::run, LONG);
	}

	/** A search-page ad: no detail fields, sellerType null. */
	private static RawListing clio(String id, int price, int km) {
		return new RawListing(Site.STANDVIRTUAL, id, "https://www.standvirtual.com/carros/anuncio/clio-" + id + ".html",
				"Renault Clio " + id, price, "Renault", "Clio", null, 2008, null, km, Fuel.PETROL, null, null, null,
				null, null, null, null, 0, null, null, false);
	}

	private static RawListing detailed(RawListing r) {
		return new RawListing(r.site(), r.externalId(), r.url(), r.title(), r.priceEur(), r.make(), r.model(),
				r.version(), r.year(), 6, r.km(), r.fuel(), 1149, 75, "manual", 5, null, "Braga", "Braga", 8,
				"Bom estado.", false, false);
	}

	private UUID start(SearchService s) {
		return s.start(LIMITS, Preset.BALANCED, null, Set.of(Site.STANDVIRTUAL));
	}

	@Test
	void start_runsSourcesAndMarksDone() {
		RawListing a = clio("a", 2000, 150000);
		RawListing b = clio("b", 2400, 180000);
		when(source.search(any(), any())).thenReturn(List.of(a, b));
		when(source.details(a.url())).thenReturn(detailed(a));
		when(source.details(b.url())).thenReturn(detailed(b));
		SearchService s = service();

		UUID id = start(s);

		RunView v = s.status(id);
		assertThat(v.status()).isEqualTo(RunStatus.DONE);
		assertThat(v.collected()).isEqualTo(2);
		assertThat(v.messagePt()).contains("2 anúncios");
		verify(source).search(org.mockito.ArgumentMatchers.eq(new SearchQuery(5000, 300000, 1995)), any());
		assertThat(listings.findAll()).allMatch(l -> l.getDetailFetchedAt() != null);
		// results() reads the limits back from JSON and ranks the run's listings.
		ScoringService.Ranking r = s.results(id);
		assertThat(r.ranked()).extracting(x -> x.listing().getExternalId()).containsExactlyInAnyOrder("a", "b");
		assertThat(r.ranked()).allMatch(ScoredListing::top5);
	}

	@Test
	void secondStartWhileRunning_rejected() throws Exception {
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		when(source.search(any(), any())).thenAnswer(inv -> {
			entered.countDown();
			release.await(5, TimeUnit.SECONDS);
			return List.of();
		});
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			SearchService s = service(executor, LONG);
			UUID id = start(s);
			assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

			assertThatThrownBy(() -> start(s)).isInstanceOf(IllegalStateException.class);

			release.countDown();
			executor.shutdown();
			assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
			assertThat(s.status(id).status()).isEqualTo(RunStatus.DONE);
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void sourceException_failsWithMessage() {
		when(source.search(any(), any())).thenThrow(new SourceException("Standvirtual mudou o formato da página"));
		SearchService s = service();

		UUID id = start(s);

		RunView v = s.status(id);
		assertThat(v.status()).isEqualTo(RunStatus.FAILED);
		assertThat(v.messagePt()).isEqualTo("Standvirtual mudou o formato da página");
		assertThat(runs.findById(id).orElseThrow().getFinishedAt()).isNotNull();
		// A failed run frees the slot for the next one.
		doReturn(List.of()).when(source).search(any(), any());
		assertThat(s.status(start(s)).status()).isEqualTo(RunStatus.DONE);
	}

	@Test
	void unexpectedError_failsAndFreesSlot() {
		when(source.search(any(), any())).thenThrow(new NullPointerException("bug"));
		SearchService s = service();

		UUID id = start(s);

		assertThat(s.status(id).status()).isEqualTo(RunStatus.FAILED);
		assertThat(s.status(id).messagePt()).isNotBlank();
		doReturn(List.of()).when(source).search(any(), any());
		assertThat(s.status(start(s)).status()).isEqualTo(RunStatus.DONE);
	}

	@Test
	void needsUser_thenSucceeds_returnsToRunning() {
		RawListing a = clio("a", 2000, 150000);
		List<RunStatus> seen = new ArrayList<>();
		List<String> messages = new ArrayList<>();
		// The run id is only known after start() returns, so the answers read the single run row.
		when(source.search(any(), any()))
				.thenThrow(new NeedsUserException("Resolve a verificação na janela do browser"))
				.thenAnswer(inv -> {
					SearchRun r = runs.findAll().getFirst();
					seen.add(r.getStatus());
					messages.add(r.getMessagePt());
					return List.of(a);
				});
		when(source.details(a.url())).thenAnswer(inv -> {
			seen.add(runs.findAll().getFirst().getStatus());
			return detailed(a);
		});
		SearchService s = service();

		UUID id = start(s);

		assertThat(seen).containsExactly(RunStatus.NEEDS_USER, RunStatus.RUNNING);
		assertThat(messages).containsExactly("Resolve a verificação na janela do browser");
		assertThat(s.status(id).status()).isEqualTo(RunStatus.DONE);
	}

	@Test
	void needsUserTimeout_fails() {
		when(source.search(any(), any())).thenThrow(new NeedsUserException("Faz login na janela do browser"));
		SearchService s = service(Runnable::run, Duration.ofMillis(50));

		UUID id = start(s);

		assertThat(s.status(id).status()).isEqualTo(RunStatus.FAILED);
		assertThat(s.status(id).messagePt()).isEqualTo("Tempo esgotado à espera do login/verificação");
	}

	@Test
	void needsUserTimeoutDuringDetails_failsRunInsteadOfSkippingAd() {
		RawListing a = clio("a", 2000, 150000);
		when(source.search(any(), any())).thenReturn(List.of(a));
		when(source.details(anyString())).thenThrow(new NeedsUserException("Resolve a verificação"));
		SearchService s = service(Runnable::run, Duration.ofMillis(50));

		UUID id = start(s);

		assertThat(s.status(id).status()).isEqualTo(RunStatus.FAILED);
		assertThat(s.status(id).messagePt()).isEqualTo("Tempo esgotado à espera do login/verificação");
	}

	@Test
	void staleRunsFailedOnStartup() {
		Listing l = store.upsert(clio("a", 2000, 150000));
		SearchRun running = new SearchRun(UUID.randomUUID(), "{}", Preset.BALANCED, null,
				Set.of(Site.STANDVIRTUAL, Site.MARKETPLACE), Instant.now());
		running.addListings(List.of(l.getId()));
		runs.save(running);
		SearchRun waiting = new SearchRun(UUID.randomUUID(), "{}", Preset.CUSTOM, "{}", Set.of(Site.MARKETPLACE),
				Instant.now());
		waiting.update(RunStatus.NEEDS_USER, "Faz login");
		runs.save(waiting);
		SearchRun done = new SearchRun(UUID.randomUUID(), "{}", Preset.BALANCED, null, Set.of(Site.STANDVIRTUAL),
				Instant.now());
		done.finish(RunStatus.DONE, "Pesquisa concluída", Instant.now());
		runs.save(done);

		service().failStaleRuns();

		for (UUID id : List.of(running.getId(), waiting.getId())) {
			SearchRun r = runs.findById(id).orElseThrow();
			assertThat(r.getStatus()).isEqualTo(RunStatus.FAILED);
			assertThat(r.getMessagePt()).isEqualTo("A aplicação foi fechada durante a pesquisa");
			assertThat(r.getFinishedAt()).isNotNull();
		}
		SearchRun reloaded = runs.findById(running.getId()).orElseThrow();
		assertThat(reloaded.getSites()).containsExactlyInAnyOrder(Site.STANDVIRTUAL, Site.MARKETPLACE);
		assertThat(reloaded.getListingIds()).containsExactly(l.getId());
		assertThat(reloaded.getCollected()).isEqualTo(1);
		assertThat(runs.findById(done.getId()).orElseThrow().getStatus()).isEqualTo(RunStatus.DONE);
	}

	@Test
	void detailsFetchedOnlyForPrefilterSurvivors() {
		RawListing good = clio("good", 2000, 150000);
		RawListing tooDear = clio("dear", 9000, 150000);
		RawListing fresh = clio("fresh", 2200, 160000);
		// fresh was read less than 7 days ago
		store.applyDetails(store.upsert(fresh), detailed(fresh));
		when(source.search(any(), any())).thenReturn(List.of(good, tooDear, fresh));
		when(source.details(good.url())).thenReturn(detailed(good));

		start(service());

		verify(source).details(good.url());
		verify(source, never()).details(tooDear.url());
		verify(source, never()).details(fresh.url());
	}

	@Test
	void oneFailedDetails_keepsAdAndCountsFailure() {
		RawListing a = clio("a", 2000, 150000);
		RawListing gone = clio("gone", 2100, 170000);
		when(source.search(any(), any())).thenReturn(List.of(a, gone));
		when(source.details(a.url())).thenReturn(detailed(a));
		when(source.details(gone.url())).thenThrow(new SourceException("Standvirtual respondeu com erro HTTP 404"));
		SearchService s = service();

		UUID id = start(s);

		RunView v = s.status(id);
		assertThat(v.status()).isEqualTo(RunStatus.DONE);
		assertThat(v.collected()).isEqualTo(2);
		assertThat(v.messagePt()).contains("1 anúncio");
		assertThat(s.results(id).ranked()).extracting(x -> x.listing().getExternalId()).contains("gone");
	}

	@Test
	void everyDetailsFails_atLeastThree_failsRunAsFormatChanged() {
		List<RawListing> ads = List.of(clio("a", 2000, 150000), clio("b", 2100, 160000), clio("c", 2200, 170000));
		when(source.search(any(), any())).thenReturn(ads);
		when(source.details(anyString())).thenThrow(new SourceException("Standvirtual respondeu com erro HTTP 500"));
		SearchService s = service();

		UUID id = start(s);

		RunView v = s.status(id);
		assertThat(v.status()).isEqualTo(RunStatus.FAILED);
		assertThat(v.messagePt()).isEqualTo("Standvirtual mudou o formato da página");
	}

	@Test
	void everyDetailsFails_onlyTwo_stillDone() {
		List<RawListing> ads = List.of(clio("a", 2000, 150000), clio("b", 2100, 160000));
		when(source.search(any(), any())).thenReturn(ads);
		when(source.details(anyString())).thenThrow(new SourceException("Standvirtual respondeu com erro HTTP 404"));
		SearchService s = service();

		assertThat(s.status(start(s)).status()).isEqualTo(RunStatus.DONE);
	}

	@Test
	void start_centreAndRadiusRequired_unknownCentreRejected() {
		SearchService s = service();
		Set<Site> sites = Set.of(Site.STANDVIRTUAL);
		for (SearchLimits bad : new SearchLimits[] {withCentre(null, 65), withCentre(" ", 65),
				withCentre("Ponte de Lima", null), withCentre("Ponte de Lima", 0), withCentre("Ponte de Lima", -5)}) {
			assertThatThrownBy(() -> s.start(bad, Preset.BALANCED, null, sites))
					.isInstanceOf(IllegalArgumentException.class).hasMessage("Indica o centro e o raio da pesquisa");
		}
		assertThatThrownBy(() -> s.start(withCentre("Atlantida", 65), Preset.BALANCED, null, sites))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("Localidade desconhecida: Atlantida");
		assertThat(runs.count()).isZero();
		assertThat(s.start(withCentre("ponte de lima", 65), Preset.BALANCED, null, sites)).isNotNull();
	}

	private static SearchLimits withCentre(String centre, Integer radius) {
		return new SearchLimits(5000, PriceCap.ASKING, 300000, 1995, EnumSet.allOf(Fuel.class), centre, radius,
				List.of(), null, null, null);
	}

	@Test
	void oldRunLimitsWithDistricts_stillLoad() {
		UUID id = UUID.randomUUID();
		String old = "{\"maxPriceEur\":5000,\"priceCap\":\"ASKING\",\"maxKm\":300000,\"minYear\":1995,"
				+ "\"fuels\":[\"PETROL\"],\"districts\":[\"Braga\"],\"models\":[]}";
		runs.save(new SearchRun(id, old, Preset.BALANCED, null, Set.of(Site.STANDVIRTUAL), Instant.now()));

		ScoringService.Ranking r = service().results(id);

		assertThat(r.ranked()).isEmpty();
		assertThat(r.excluded()).isEmpty();
	}

	@Test
	void start_siteWithoutSource_rejected() {
		SearchService s = service();

		assertThatThrownBy(() -> s.start(LIMITS, Preset.BALANCED, null, Set.of()))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> s.start(LIMITS, Preset.CUSTOM, null, Set.of(Site.STANDVIRTUAL)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(runs.count()).isZero();
	}

	@Test
	void scoreLink_readsAdvertAndRanksItAlone() {
		RawListing a = clio("a", 2000, 150000);
		when(source.details(a.url())).thenReturn(detailed(a));

		ScoringService.Ranking r = service().scoreLink(Site.STANDVIRTUAL, a.url(), null, null, null);

		assertThat(r.ranked()).hasSize(1);
		Listing l = r.ranked().getFirst().listing();
		assertThat(l.getDetailFetchedAt()).isNotNull();
		assertThat(l.getPhotoCount()).isEqualTo(8);
		assertThat(listings.count()).isEqualTo(1);
	}

	private static RawListing marketplace(String id, int price) {
		return new RawListing(Site.MARKETPLACE, id, "https://www.facebook.com/marketplace/item/" + id + "/",
				"2008 Renault Clio 1.2", price, "Renault", "Clio", null, 2008, null, 150000, null, null, null, null,
				null, null, null, "Braga", 0, null, null, false);
	}

	@Test
	void marketplaceRunRanksAllStoredMarketplaceAds_noSourceBean() {
		store.upsert(marketplace("m1", 2000));
		store.upsert(marketplace("m2", 1)); // "1.00" in the HAR: placeholder price
		store.upsert(clio("s1", 2000, 150000)); // another site's ad is not part of a Marketplace-only run
		SearchService s = service();

		UUID id = s.start(LIMITS, Preset.BALANCED, null, Set.of(Site.MARKETPLACE));

		assertThat(s.status(id).status()).isEqualTo(RunStatus.DONE);
		assertThat(s.status(id).collected()).isEqualTo(2);
		verify(source, never()).search(any(), any());
		verify(source, never()).details(anyString());
		ScoringService.Ranking r = s.results(id);
		assertThat(r.ranked()).extracting(x -> x.listing().getExternalId()).containsExactly("m1");
		assertThat(r.excluded()).singleElement().satisfies(e -> {
			assertThat(e.listing().getExternalId()).isEqualTo("m2");
			assertThat(e.reasonsPt()).anyMatch(x -> x.startsWith("preço simbólico"));
		});
	}

	@Test
	void whileIdle_sharesTheRunSlot() throws Exception {
		CountDownLatch inside = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService pool = Executors.newSingleThreadExecutor();
		SearchService s = service(pool, LONG);
		try {
			when(source.search(any(), any())).thenAnswer(inv -> {
				inside.countDown();
				release.await(5, TimeUnit.SECONDS);
				return List.of();
			});
			start(s);
			assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();

			assertThatThrownBy(() -> s.whileIdle(() -> 1)).isInstanceOf(IllegalStateException.class)
					.hasMessage("Já há uma pesquisa a decorrer");
		}
		finally {
			release.countDown();
			pool.shutdown();
			pool.awaitTermination(5, TimeUnit.SECONDS);
		}
		assertThat(s.whileIdle(() -> 7)).isEqualTo(7);
		// an import in progress blocks a search the same way
		assertThatThrownBy(() -> s.whileIdle(() -> start(s))).isInstanceOf(IllegalStateException.class);
	}

	private SearchService withMarketplace(ListingSource mp) {
		when(mp.site()).thenReturn(Site.MARKETPLACE);
		when(mp.maxDetailsPerRun()).thenReturn(20);
		return new SearchService(List.of(source, mp), store, listings, runs, scoring, JsonMapper.builder().build(),
				Clock.systemUTC(), Runnable::run, LONG, Duration.ofMillis(10), PLACES);
	}

	@Test
	void budget_readsAtMost20_bestRankFirst() {
		for (int i = 21; i >= 0; i--) { // stored dearest first, so storage order is not rank order
			RawListing m = marketplace("m" + i, 2000 + 10 * i);
			store.upsert(new RawListing(m.site(), m.externalId(), m.url(), m.title(), m.priceEur(), m.make(),
					m.model(), null, m.year(), null, 100000 + 10000 * i, null, null, null, null, null, null, null,
					m.city(), 0, null, null, false)); // km apart, so no two are the same car
		}
		ListingSource mp = mock(ListingSource.class);
		List<String> read = new ArrayList<>();
		when(mp.details(anyString())).thenAnswer(inv -> {
			read.add(inv.getArgument(0));
			throw new SourceException("Marketplace respondeu com erro HTTP 404");
		});
		List<String> expected = scoring.rank(listings.findBySite(Site.MARKETPLACE), listings.findAll(), LIMITS,
				Preset.BALANCED, null).ranked().stream().limit(20).map(x -> x.listing().getUrl()).toList();
		SearchService s = withMarketplace(mp);

		s.start(LIMITS, Preset.BALANCED, null, Set.of(Site.MARKETPLACE));

		assertThat(expected).hasSize(20).isNotEqualTo(listings.findBySite(Site.MARKETPLACE).stream().limit(20)
				.map(Listing::getUrl).toList());
		assertThat(read).containsExactlyElementsOf(expected);
	}

	@Test
	void allPagesHitLoginWall_runIsDoneNotFailed() {
		for (int i = 0; i < 3; i++) {
			store.upsert(marketplace("w" + i, 2000 + i));
		}
		ListingSource mp = mock(ListingSource.class);
		when(mp.details(anyString())).thenThrow(new carfinder.source.marketplace.LoginWallException());
		SearchService s = withMarketplace(mp);

		UUID id = s.start(LIMITS, Preset.BALANCED, null, Set.of(Site.MARKETPLACE));

		RunView v = s.status(id);
		assertThat(v.status()).isEqualTo(RunStatus.DONE);
		assertThat(v.messagePt()).contains("Não foi possível ler 3 anúncios em detalhe");
		assertThat(listings.findAll()).allMatch(l -> l.getDetailFetchedAt() == null);
	}

	@Test
	void marketplaceDetails_fillGapsOnly_importedKmAndYearKept() {
		store.upsert(marketplace("g1", 2000)); // imported: 2008, 150000 km
		RawListing noKm = marketplace("g2", 2100);
		store.upsert(new RawListing(noKm.site(), noKm.externalId(), noKm.url(), noKm.title(), noKm.priceEur(),
				noKm.make(), noKm.model(), null, null, null, null, null, null, null, null, null, null, null,
				noKm.city(), 0, null, null, false));
		ListingSource mp = mock(ListingSource.class);
		when(mp.details(anyString())).thenAnswer(inv -> {
			String url = inv.getArgument(0);
			RawListing m = marketplace(url.contains("g1") ? "g1" : "g2", 2000);
			// "revisão aos 500 km" in a description must not replace the imported km
			return new RawListing(m.site(), m.externalId(), m.url(), m.title(), m.priceEur(), m.make(), m.model(),
					null, 2011, null, 500, Fuel.DIESEL, null, null, "manual", null, null, null, m.city(), 4,
					"Revisão aos 500 km.", null, false);
		});
		SearchService s = withMarketplace(mp);

		s.start(LIMITS, Preset.BALANCED, null, Set.of(Site.MARKETPLACE));

		Listing g1 = listings.findBySiteAndExternalId(Site.MARKETPLACE, "g1").orElseThrow();
		assertThat(g1.getDetailFetchedAt()).isNotNull();
		assertThat(g1.getKm()).isEqualTo(150000);
		assertThat(g1.getYear()).isEqualTo(2008);
		assertThat(g1.getGearbox()).isEqualTo("manual");
		Listing g2 = listings.findBySiteAndExternalId(Site.MARKETPLACE, "g2").orElseThrow();
		assertThat(g2.getKm()).isEqualTo(500);
		assertThat(g2.getYear()).isEqualTo(2011);
	}

	@Test
	void scoreLink_marketplaceKeepsImportedKm() {
		RawListing imported = marketplace("p1", 2000);
		store.upsert(imported);
		ListingSource mp = mock(ListingSource.class);
		when(mp.details(imported.url())).thenReturn(new RawListing(imported.site(), "p1", imported.url(),
				imported.title(), 2000, "Renault", "Clio", null, 2011, null, 500, null, null, null, null, null, null,
				null, "Braga", 4, "Revisão aos 500 km.", null, false));

		withMarketplace(mp).scoreLink(Site.MARKETPLACE, imported.url(), null, null, null);

		Listing p1 = listings.findBySiteAndExternalId(Site.MARKETPLACE, "p1").orElseThrow();
		assertThat(p1.getKm()).isEqualTo(150000);
		assertThat(p1.getYear()).isEqualTo(2008);
		assertThat(p1.getDescription()).isEqualTo("Revisão aos 500 km.");
	}

	@Test
	void soldPage_marksAdUnavailable_notAFailure() {
		for (int i = 0; i < 3; i++) {
			store.upsert(marketplace("v" + i, 2000 + i));
		}
		ListingSource mp = mock(ListingSource.class);
		when(mp.details(anyString())).thenThrow(new carfinder.source.marketplace.SoldOrReservedException());
		SearchService s = withMarketplace(mp);

		UUID id = s.start(LIMITS, Preset.BALANCED, null, Set.of(Site.MARKETPLACE));

		RunView v = s.status(id);
		assertThat(v.status()).isEqualTo(RunStatus.DONE);
		assertThat(v.messagePt()).doesNotContain("Não foi possível ler");
		ScoringService.Ranking r = s.results(id);
		assertThat(r.ranked()).isEmpty();
		assertThat(r.excluded()).hasSize(3).allSatisfy(e -> assertThat(e.reasonsPt()).contains("vendido ou reservado"));
	}
}
