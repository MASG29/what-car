package carfinder.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import carfinder.buying.ChecklistBuilder;
import carfinder.buying.RealCostCalculator;
import carfinder.buying.RealCostSettings;
import carfinder.geo.Places;
import carfinder.knowledge.KnowledgeBase;
import carfinder.knowledge.KnowledgeLoader;
import carfinder.listing.Fuel;
import carfinder.listing.Listing;
import carfinder.listing.RawListing;
import carfinder.listing.Site;
import carfinder.listing.TestListings;
import carfinder.matching.CarMatcher;
import carfinder.search.ListingFilter;
import carfinder.search.SearchLimits;
import carfinder.search.SearchLimits.PriceCap;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScoringServiceTest {

	private static final KnowledgeBase KB = KnowledgeLoader
			.load(Paths.get("src/test/resources/fixtures/knowledge-reliability"));
	private final ScoringService service = new ScoringService(new CarMatcher(KB), new ReliabilityScorer(KB),
			new PriceScorer(new PriceProperties(1.25, 0.80)), new ConsumptionScorer(KB), new CompletenessScorer(),
			new PhotoScorer(), new RealCostCalculator(), new ChecklistBuilder(), new ListingFilter(KB, new Places()),
			Clock.fixed(Instant.parse("2026-10-31T00:00:00Z"), ZoneOffset.UTC));
	private static final SearchLimits LIMITS = new SearchLimits(5000, PriceCap.ASKING, 300000, 1995,
			EnumSet.allOf(Fuel.class), null, null, List.of(), RealCostSettings.defaults(), null, null);

	static Listing clio(Site site, String id, Integer price, Integer year, Integer km, boolean parts) {
		return TestListings.of(new RawListing(site, id, "https://example.test/" + id, "Clio " + id, price, "Renault",
				"Clio", null, year, 6, km, Fuel.PETROL, null, null, null, null, null, null, null, 0, null, null,
				parts), false);
	}

	static SubScore sub(double v) {
		return new SubScore(OptionalDouble.of(v), 1.0, "", List.of(), List.of());
	}

	static Map<Criterion, SubScore> subs(Map<Criterion, SubScore> present) {
		Map<Criterion, SubScore> all = new EnumMap<>(Criterion.class);
		for (Criterion c : Criterion.values()) {
			all.put(c, present.getOrDefault(c, new SubScore(OptionalDouble.empty(), 0, "sem dados", List.of(),
					List.of())));
		}
		return all;
	}

	@Test
	void rank_missingSubScore_renormalisesButLowersRank() {
		Weights w = Preset.BALANCED.weights(null);
		ScoringService.Totals onlyPriceAndPhotos = ScoringService.combine(
				subs(Map.of(Criterion.PRICE, sub(0.6), Criterion.PHOTOS, sub(0.6))), w);
		ScoringService.Totals documented = ScoringService.combine(subs(Map.of(Criterion.PRICE, sub(0.6),
				Criterion.PHOTOS, sub(0.6), Criterion.RELIABILITY, sub(0.5))), w);

		// Missing reliability is left out, not counted as 0 ...
		assertThat(onlyPriceAndPhotos.total()).isCloseTo(0.6, within(1e-9));
		assertThat(onlyPriceAndPhotos.confidence()).isCloseTo(0.4, within(1e-9));
		assertThat(onlyPriceAndPhotos.rank()).isCloseTo(0.24 * 0.7, within(1e-9));
		assertThat(documented.total()).isCloseTo(0.39 / 0.7, within(1e-9));
		assertThat(documented.confidence()).isCloseTo(0.7, within(1e-9));
		// ... but lower confidence keeps it below the documented ad despite its higher total.
		assertThat(onlyPriceAndPhotos.total()).isGreaterThan(documented.total());
		assertThat(documented.rank()).isGreaterThan(onlyPriceAndPhotos.rank());
	}

	@Test
	void rank_missingCriteriaAddNothing_sparsePerfectAdBelowDocumentedAverageAd() {
		Weights w = Preset.BALANCED.weights(null);
		ScoringService.Totals sparse = ScoringService.combine(
				subs(Map.of(Criterion.COMPLETENESS, sub(1.0), Criterion.PHOTOS, sub(1.0))), w);
		Map<Criterion, SubScore> allFive = new EnumMap<>(Criterion.class);
		for (Criterion c : Criterion.values()) {
			allFive.put(c, sub(0.6));
		}
		ScoringService.Totals documented = ScoringService.combine(allFive, w);

		assertThat(sparse.total()).isCloseTo(1.0, within(1e-9)); // shown total unchanged
		assertThat(sparse.rank()).isCloseTo(0.25 * 0.625, within(1e-9));
		assertThat(documented.rank()).isCloseTo(0.6, within(1e-9));
		assertThat(documented.rank()).isGreaterThan(sparse.rank());
	}

	@Test
	void noValues_totalZero() {
		assertThat(ScoringService.combine(subs(Map.of()), Preset.BALANCED.weights(null)).total()).isZero();
	}

	@Test
	void rank_top5Flagged() {
		List<Listing> ads = new ArrayList<>();
		for (int i = 0; i < 7; i++) {
			ads.add(clio(Site.STANDVIRTUAL, "c" + i, 1000 + i * 100, 2008, 100000 + i * 10000, false));
		}
		List<ScoredListing> ranked = service.rank(ads, ads, LIMITS, Preset.BALANCED, null).ranked();
		assertThat(ranked).hasSize(7);
		assertThat(ranked).extracting(ScoredListing::top5).containsExactly(true, true, true, true, true, false,
				false);
		assertThat(ranked).extracting(ScoredListing::rank).isSortedAccordingTo((a, b) -> Double.compare(b, a));
		assertThat(ranked.getFirst().listing().getPriceEur()).isEqualTo(1000); // cheapest scores best on price
		assertThat(ranked.getFirst().subs()).containsOnlyKeys(Criterion.values());
		assertThat(ranked.getFirst().realCost().itemsPt()).containsEntry("Preço pedido", 1000.0);
		assertThat(ranked.getFirst().checklist().links()).isNotEmpty();
	}

	@Test
	void rank_excludedAdsCarryReasons() {
		Listing parts = clio(Site.STANDVIRTUAL, "p", 1500, 2008, 100000, true);
		Listing bare = TestListings.of(new RawListing(Site.MARKETPLACE, "b", "https://example.test/b", "t", null,
				null, null, null, null, null, null, null, null, null, null, null, null, null, null, 0, null, null,
				false), false);
		ScoringService.Ranking r = service.rank(List.of(parts, bare), List.of(), LIMITS, Preset.RELIABLE, null);
		assertThat(r.ranked()).isEmpty();
		assertThat(r.excluded()).extracting(Excluded::listing).containsExactly(parts, bare);
		assertThat(r.excluded()).allSatisfy(e -> assertThat(e.reasonsPt()).isNotEmpty());
	}

	@Test
	void rank_duplicatesShownOnce_withAlsoOnUrls() {
		Listing sv = clio(Site.STANDVIRTUAL, "sv", 1500, 2008, 100000, false);
		Listing mp = clio(Site.MARKETPLACE, "mp", 1520, 2008, 100400, false);
		List<ScoredListing> ranked = service.rank(List.of(sv, mp), List.of(), LIMITS, Preset.BALANCED, null)
				.ranked();
		assertThat(ranked).hasSize(1);
		assertThat(ranked.getFirst().alsoOnUrls()).containsExactly("https://example.test/mp");
	}

	@Test
	void rank_missingFields_scoredWithWarningsNotDropped() {
		Listing noYearNoKm = clio(Site.MARKETPLACE, "n", 1500, null, null, false);
		List<ScoredListing> ranked = service.rank(List.of(noYearNoKm), List.of(), LIMITS, Preset.BALANCED, null)
				.ranked();
		assertThat(ranked).hasSize(1);
		assertThat(ranked.getFirst().notesPt()).contains("ano não indicado");
		assertThat(ranked.getFirst().warningsPt()).containsOnlyOnce("km não indicados");
		assertThat(ranked.getFirst().confidence()).isLessThan(0.5);
	}

	static Listing clioSaying(String id, String description) {
		return clioSaying(id, description, 5);
	}

	static Listing clioSaying(String id, String description, int photos) {
		return TestListings.of(new RawListing(Site.STANDVIRTUAL, id, "https://example.test/" + id, "Clio " + id, 1500,
				"Renault", "Clio", null, 2008, 6, 100000, Fuel.PETROL, null, null, null, null, null, null, null, photos,
				description, null, false), true);
	}

	@Test
	void rank_conditionHints_adjustRankNotTotal() {
		Listing plain = clioSaying("plain", null);
		Listing scratched = clioSaying("scratched", "Tem riscos e alguma ferrugem.");
		Listing serviced = clioSaying("serviced", "Embraiagem nova.");
		List<ScoredListing> ranked = service.rank(List.of(plain, scratched, serviced), List.of(), LIMITS,
				Preset.BALANCED, null).ranked();

		assertThat(ranked).extracting(x -> x.listing().getExternalId()).containsExactly("serviced", "plain",
				"scratched");
		ScoredListing s = ranked.get(0);
		ScoredListing p = ranked.get(1);
		ScoredListing x = ranked.get(2);
		assertThat(s.total()).isEqualTo(p.total()).isEqualTo(x.total());
		assertThat(s.subs()).isEqualTo(p.subs());
		assertThat(s.rank()).isCloseTo(p.rank() + 0.03, within(1e-9));
		assertThat(x.rank()).isCloseTo(Math.max(0, p.rank() - 0.10), within(1e-9));
		assertThat(x.warningsPt()).contains("O anúncio menciona: riscos", "O anúncio menciona: ferrugem");
		assertThat(s.notesPt()).contains("Manutenção recente: embraiagem");
		assertThat(x.condition().problemsPt()).containsExactly("riscos", "ferrugem");
		assertThat(s.whyPt()).startsWith("1.º lugar: ").endsWith("+0,03 por manutenção recente (embraiagem).");
		assertThat(x.whyPt()).startsWith("3.º lugar: ").endsWith("−0,10 por riscos, ferrugem mencionados.");
	}

	@Test
	void rank_conditionPenalty_flooredAtZero() {
		Listing bad = clioSaying("bad", "riscos, amolgadelas, ferrugem, fumo", 1); // photos 0.1, rank 0.1
		ScoringService.Ranking r = service.rank(List.of(bad), List.of(), LIMITS,
				Preset.CUSTOM, new Weights(0, 0, 0, 0, 1));
		assertThat(r.ranked().getFirst().rank()).isZero();
	}

	private static SearchLimits showingDamaged() {
		return new SearchLimits(5000, PriceCap.ASKING, 300000, 1995, EnumSet.allOf(Fuel.class), null, null,
				List.of(), RealCostSettings.defaults(), null, true);
	}

	@Test
	void rank_damagedAd_droppedByDefault() {
		Listing bad = clioSaying("bad", "Motor gripado.");
		ScoringService.Ranking r = service.rank(List.of(bad), List.of(), LIMITS, Preset.BALANCED, null);
		assertThat(r.ranked()).isEmpty();
		assertThat(r.excluded()).singleElement().satisfies(e -> assertThat(e.reasonsPt())
				.containsExactly("precisa de reparação (motor gripado)"));
	}

	@Test
	void rank_damagedAdShown_penalisedWithWarningAndNote() {
		Listing plain = clioSaying("plain", null);
		Listing bad = clioSaying("bad", "Motor gripado.");
		List<ScoredListing> ranked = service.rank(List.of(plain, bad), List.of(), showingDamaged(), Preset.BALANCED,
				null).ranked();

		assertThat(ranked).extracting(x -> x.listing().getExternalId()).containsExactly("plain", "bad");
		ScoredListing p = ranked.get(0);
		ScoredListing x = ranked.get(1);
		assertThat(x.total()).isEqualTo(p.total());
		assertThat(x.rank()).isCloseTo(Math.max(0, p.rank() - 0.15), within(1e-9));
		assertThat(x.warningsPt()).contains("Precisa de reparação: motor gripado");
		assertThat(x.notesPt()).contains("custo da reparação: sem dados");
		assertThat(x.whyPt()).endsWith("−0,15 por precisa de reparação (motor gripado).");
	}

	@Test
	void rank_damagePenaltyAddsToProblems_flooredAtZero() {
		Listing bad = clioSaying("bad", "Batido, riscos, ferrugem.", 1);
		ScoringService.Ranking r = service.rank(List.of(bad), List.of(), showingDamaged(), Preset.CUSTOM,
				new Weights(0, 0, 0, 0, 1));
		assertThat(r.ranked().getFirst().rank()).isZero();
	}

	@Test
	void customWeights_normalised() {
		Weights w = Preset.CUSTOM.weights(new Weights(2, 2, 1, 0, 0));
		assertThat(w.of(Criterion.PRICE)).isCloseTo(0.4, within(1e-9));
		assertThat(w.of(Criterion.RELIABILITY)).isCloseTo(0.4, within(1e-9));
		assertThat(w.of(Criterion.CONSUMPTION)).isCloseTo(0.2, within(1e-9));
		assertThat(w.of(Criterion.COMPLETENESS)).isZero();
		assertThat(w.of(Criterion.PHOTOS)).isZero();
		assertThatThrownBy(() -> Preset.CUSTOM.weights(new Weights(0, 0, 0, 0, 0)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Preset.CUSTOM.weights(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void presets_matchPlan() {
		assertThat(Preset.BALANCED.weights(null)).isEqualTo(new Weights(0.30, 0.30, 0.15, 0.15, 0.10));
		assertThat(Preset.ECONOMICAL.weights(null)).isEqualTo(new Weights(0.40, 0.15, 0.30, 0.10, 0.05));
		assertThat(Preset.RELIABLE.weights(null)).isEqualTo(new Weights(0.20, 0.50, 0.10, 0.15, 0.05));
	}
}
