package carfinder.scoring;

import static carfinder.listing.TestListings.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import carfinder.knowledge.Generation;
import carfinder.listing.Fuel;
import carfinder.listing.Listing;
import carfinder.listing.RawListing;
import carfinder.listing.Site;
import carfinder.matching.CarMatch;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PriceScorerTest {

	private static final Generation CLIO3 = new Generation("r-clio-3", "Renault", "Clio", "III", "B",
			YearMonth.of(2005, 1), YearMonth.of(2012, 6), List.of(), List.of());
	private static final CarMatch MATCH = new CarMatch(Optional.of(CLIO3), List.of(CLIO3), false, Optional.empty(),
			List.of(), List.of(), false);
	private final PriceScorer scorer = new PriceScorer(new PriceProperties(1.25, 0.80));

	private static Listing ad(String id, Integer price, int year, Integer km, boolean parts) {
		return ad(id, "Renault", "Clio", price, year, km, parts);
	}

	private static Listing ad(String id, String make, String model, Integer price, int year, Integer km,
			boolean parts) {
		return of(new RawListing(Site.STANDVIRTUAL, id, "https://example.test/" + id, "t", price, make, model, null,
				year, 6, km, Fuel.PETROL, null, null, null, null, null, null, null, 0, null, null, parts), false);
	}

	/** Ten comparables priced 1000..1900, median 1450. */
	private static List<Listing> pool() {
		List<Listing> pool = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			pool.add(ad("p" + i, 1000 + i * 100, 2009, 100000, false));
		}
		return pool;
	}

	private PriceResult score(int price, List<Listing> pool) {
		return scorer.score(ad("self", price, 2009, 100000, false), MATCH, pool);
	}

	@Test
	void price_fewerThanThreeComparables_empty() {
		PriceResult r = score(1450, pool().subList(0, 2));
		assertThat(r.score().value()).isEmpty();
		assertThat(r.score().explanationPt()).isEqualTo("poucos anúncios comparáveis (2)");
		assertThat(r.ratio()).isEmpty();
		assertThat(r.inflated()).isFalse();
		assertThat(r.wellBelow()).isFalse();
	}

	@Test
	void price_medianRatio_scored() {
		PriceResult r = score(1450, pool());
		assertThat(r.ratio().getAsDouble()).isCloseTo(1.0, within(1e-9));
		assertThat(r.score().value().getAsDouble()).isCloseTo(0.5, within(1e-9));
		assertThat(r.score().confidence()).isEqualTo(1.0);
		assertThat(score(1450, pool().subList(0, 5)).score().confidence()).isEqualTo(0.5);
	}

	@Test
	void price_inflatedFlag() {
		assertThat(score(1900, pool()).inflated()).isTrue(); // 1900/1450 = 1.31
		assertThat(score(1450, pool()).inflated()).isFalse();
	}

	@Test
	void price_wellBelowFlag() {
		assertThat(score(1100, pool()).wellBelow()).isTrue(); // 0.76
		assertThat(score(1450, pool()).wellBelow()).isFalse();
	}

	@Test
	void price_excludesItselfAndParts() {
		List<Listing> pool = new ArrayList<>(pool().subList(0, 2));
		pool.add(ad("self", 1450, 2009, 100000, false)); // same url as the scored ad
		pool.add(ad("parts", 1450, 2009, 100000, true));
		assertThat(score(1450, pool).score().value()).isEmpty();
		assertThat(score(1450, pool).score().explanationPt()).contains("(2)");
	}

	@Test
	void price_excludesNullAndPlaceholderPrices() {
		List<Listing> pool = new ArrayList<>(pool().subList(0, 2));
		pool.add(ad("null", null, 2009, 100000, false));
		pool.add(ad("one", 1, 2009, 100000, false));
		pool.add(ad("zero", 0, 2009, 100000, false));
		assertThat(score(1450, pool).score().explanationPt()).contains("(2)");
	}

	@Test
	void price_comparablesNeverCrossModelOrGeneration_evenWhenWidened() {
		List<Listing> pool = new ArrayList<>();
		pool.add(ad("a", 1400, 2006, 100000, false));
		pool.add(ad("b", 1500, 2006, 100000, false));
		pool.add(ad("other-model", "Renault", "Megane", 1500, 2006, 100000, false));
		pool.add(ad("other-gen", 1500, 2004, 100000, false)); // Clio II, 2 years away
		PriceResult r = scorer.score(ad("self", 1450, 2006, 100000, false), MATCH, pool);
		assertThat(r.score().value()).isEmpty();
		assertThat(r.score().explanationPt()).isEqualTo("poucos anúncios comparáveis (2)");
	}

	@Test
	void price_unknownKmSkipsKmCheck_notWidened() {
		List<Listing> pool = new ArrayList<>(pool().subList(0, 2));
		pool.add(ad("no-km", 1500, 2009, null, false));
		PriceResult r = score(1450, pool);
		assertThat(r.score().explanationPt()).contains("de 3 anúncios").doesNotContain("alargada");
		assertThat(r.score().confidence()).isCloseTo(0.3, within(1e-9));
	}

	@Test
	void price_tooFew_dropsKmRuleFirst_andStopsThere() {
		List<Listing> pool = new ArrayList<>(pool().subList(0, 2));
		pool.add(ad("far-km", 1500, 2009, 160000, false));
		pool.add(ad("far-year", 1500, 2012, 100000, false)); // only counts at the +-3 years step
		PriceResult r = score(1450, pool);
		assertThat(r.score().value()).isPresent();
		assertThat(r.score().explanationPt()).contains("de 3 anúncios comparáveis (comparação alargada)");
		assertThat(r.score().confidence()).isCloseTo(0.3 * 0.7, within(1e-9));
	}

	@Test
	void price_widened_neverFlagsInflatedOrWellBelow() {
		List<Listing> pool = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			pool.add(ad("high-km" + i, 1000, 2009, 280000, false));
		}
		PriceResult dear = scorer.score(ad("self", 1900, 2009, 120000, false), MATCH, pool); // ratio 1.9
		assertThat(dear.score().explanationPt()).contains("comparação alargada");
		assertThat(dear.inflated()).isFalse();
		PriceResult cheap = scorer.score(ad("self", 500, 2009, 120000, false), MATCH, pool); // ratio 0.5
		assertThat(cheap.wellBelow()).isFalse();
	}

	@Test
	void price_tooFew_thenAllowsThreeYears_notFour() {
		List<Listing> pool = new ArrayList<>(pool().subList(0, 2));
		pool.add(ad("four-years", 1500, 2005, 160000, false));
		assertThat(score(1450, pool).score().explanationPt()).isEqualTo("poucos anúncios comparáveis (2)");
		pool.add(ad("three-years", 1500, 2012, 160000, false));
		PriceResult r = score(1450, pool);
		assertThat(r.score().explanationPt()).contains("de 3 anúncios comparáveis (comparação alargada)");
		assertThat(r.score().confidence()).isCloseTo(0.3 * 0.7, within(1e-9));
	}

	@Test
	void price_withoutGeneration_usesMakeAndModel() {
		CarMatch none = new CarMatch(Optional.empty(), List.of(), false, Optional.empty(), List.of(), List.of(), false);
		List<Listing> pool = new ArrayList<>(pool().subList(0, 2));
		pool.add(ad("gen-iv", 2007, 2010, 100000, false));
		pool.add(ad("renault-clio-2011", 1500, 2011, 100000, false));
		assertThat(scorer.score(ad("self", 1450, 2009, 100000, false), none, pool).score().value()).isPresent();
	}

	@Test
	void price_ownPriceMissingOrPlaceholder_noScore() {
		PriceResult r = scorer.score(ad("self", 1, 2009, 100000, false), MATCH, pool());
		assertThat(r.score().value()).isEmpty();
		assertThat(r.score().confidence()).isZero();
		assertThat(r.inflated()).isFalse();
	}
}
