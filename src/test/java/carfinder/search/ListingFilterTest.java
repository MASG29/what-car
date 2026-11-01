package carfinder.search;

import static org.assertj.core.api.Assertions.assertThat;

import carfinder.buying.RealCost;
import carfinder.buying.RealCostSettings;
import carfinder.knowledge.KnowledgeLoader;
import carfinder.listing.Fuel;
import carfinder.listing.Listing;
import carfinder.listing.RawListing;
import carfinder.listing.Site;
import carfinder.listing.TestListings;
import carfinder.matching.CarMatch;
import carfinder.scoring.PriceResult;
import carfinder.scoring.SubScore;
import carfinder.search.SearchLimits.PriceCap;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ListingFilterTest {

	private final ListingFilter filter = new ListingFilter(
			KnowledgeLoader.load(Paths.get("src/test/resources/fixtures/knowledge")));

	private static final CarMatch NO_MATCH = new CarMatch(Optional.empty(), List.of(), false, Optional.empty(),
			List.of(), List.of(), false);
	private static final PriceResult PLAIN = price(false, false);
	private static final RealCost COST = new RealCost(1800, Map.of(), "");

	static PriceResult price(boolean inflated, boolean wellBelow) {
		return new PriceResult(new SubScore(OptionalDouble.empty(), 0, "", List.of(), List.of()),
				OptionalDouble.empty(), inflated, wellBelow);
	}

	static SearchLimits limits(PriceCap cap, List<String> models, Set<String> districts) {
		return new SearchLimits(2500, cap, 200000, 2005, EnumSet.of(Fuel.PETROL, Fuel.DIESEL), districts, models,
				RealCostSettings.defaults(), null);
	}

	static SearchLimits minDoors(Integer n) {
		return new SearchLimits(2500, PriceCap.ASKING, 200000, 2005, EnumSet.of(Fuel.PETROL, Fuel.DIESEL), Set.of(),
				List.of(), RealCostSettings.defaults(), n);
	}

	static Listing withDoors(Integer doors) {
		return TestListings.of(new RawListing(Site.STANDVIRTUAL, "x", "https://example.test/x", "t", 1500,
				"Volkswagen", "Polo", null, 2010, 6, 150000, Fuel.PETROL, null, null, null, doors, null, "Braga", null,
				8, null, null, false), true);
	}

	static SearchLimits limits() {
		return limits(PriceCap.ASKING, List.of(), Set.of());
	}

	static Listing ad(Integer price, String make, String model, Integer year, Integer km, Fuel fuel,
			String district, int photos, boolean detail, boolean parts) {
		return TestListings.of(new RawListing(Site.STANDVIRTUAL, "x", "https://example.test/x", "t", price, make,
				model, null, year, 6, km, fuel, null, null, null, null, null, district, null, photos, null, null,
				parts), detail);
	}

	static Listing ok() {
		return ad(1500, "Volkswagen", "Polo", 2010, 150000, Fuel.PETROL, "Braga", 8, true, false);
	}

	private FilterResult apply(Listing l) {
		return filter.apply(l, NO_MATCH, PLAIN, COST, limits());
	}

	private static void dropped(FilterResult r) {
		assertThat(r.keep()).isFalse();
		assertThat(r.notesPt()).isNotEmpty().allMatch(n -> !n.isBlank());
	}

	@Test
	void plainAd_kept() {
		FilterResult r = apply(ok());
		assertThat(r.keep()).isTrue();
		assertThat(r.notesPt()).isEmpty();
	}

	@Test
	void filter_dropsParts() {
		dropped(apply(ad(1500, "Volkswagen", "Polo", 2010, 150000, Fuel.PETROL, "Braga", 8, true, true)));
	}

	@Test
	void filter_dropsNoPhotos() {
		dropped(apply(ad(1500, "Volkswagen", "Polo", 2010, 150000, Fuel.PETROL, "Braga", 0, true, false)));
	}

	@Test
	void searchPageAdWithoutPhotoCount_kept() {
		assertThat(apply(ad(1500, "Volkswagen", "Polo", 2010, 150000, Fuel.PETROL, "Braga", 0, false, false)).keep())
				.isTrue();
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 1, 99 })
	void filter_dropsPlaceholderPrice(int p) {
		dropped(apply(ad(p, "Volkswagen", "Polo", 2010, 150000, Fuel.PETROL, "Braga", 8, true, false)));
	}

	@Test
	void nullPrice_dropped() {
		dropped(apply(ad(null, "Volkswagen", "Polo", 2010, 150000, Fuel.PETROL, "Braga", 8, true, false)));
	}

	@Test
	void askingCap_dropsOverPrice() {
		dropped(apply(ad(2600, "Volkswagen", "Polo", 2010, 150000, Fuel.PETROL, "Braga", 8, true, false)));
	}

	@Test
	void filter_realCostCap_usesTotal() {
		Listing l = ok(); // asking 1500, under the 2500 cap
		SearchLimits real = limits(PriceCap.REAL_COST, List.of(), Set.of());
		assertThat(filter.apply(l, NO_MATCH, PLAIN, new RealCost(2400, Map.of(), ""), real).keep()).isTrue();
		dropped(filter.apply(l, NO_MATCH, PLAIN, new RealCost(2600, Map.of(), ""), real));
	}

	@Test
	void inflatedPrice_dropped() {
		dropped(filter.apply(ok(), NO_MATCH, price(true, false), COST, limits()));
	}

	@Test
	void yearBelowMin_dropped() {
		dropped(apply(ad(1500, "Volkswagen", "Polo", 2004, 150000, Fuel.PETROL, "Braga", 8, true, false)));
	}

	@Test
	void nullYear_keptWithNote() {
		FilterResult r = apply(ad(1500, "Volkswagen", "Polo", null, 150000, Fuel.PETROL, "Braga", 8, true, false));
		assertThat(r.keep()).isTrue();
		assertThat(r.notesPt()).containsExactly("ano não indicado");
	}

	@Test
	void fuelNotWanted_dropped_unknownFuel_kept() {
		dropped(apply(ad(1500, "Volkswagen", "Polo", 2010, 150000, Fuel.LPG, "Braga", 8, true, false)));
		assertThat(apply(ad(1500, "Volkswagen", "Polo", 2010, 150000, null, "Braga", 8, true, false)).keep())
				.isTrue();
	}

	@Test
	void districtNotWanted_dropped_matchIgnoresCase() {
		SearchLimits north = limits(PriceCap.ASKING, List.of(), Set.of("Braga", "Porto"));
		dropped(filter.apply(ad(1500, "Volkswagen", "Polo", 2010, 150000, Fuel.PETROL, "Lisboa", 8, true, false),
				NO_MATCH, PLAIN, COST, north));
		assertThat(filter.apply(ad(1500, "Volkswagen", "Polo", 2010, 150000, Fuel.PETROL, "braga", 8, true, false),
				NO_MATCH, PLAIN, COST, north).keep()).isTrue();
	}

	@Test
	void filter_nullDistrict_kept() {
		SearchLimits north = limits(PriceCap.ASKING, List.of(), Set.of("Braga"));
		assertThat(filter.apply(ad(1500, "Volkswagen", "Polo", 2010, 150000, Fuel.PETROL, null, 8, true, false),
				NO_MATCH, PLAIN, COST, north).keep()).isTrue();
	}

	@Test
	void filter_overKmButWellBelow_keptWithNote() {
		Listing l = ad(1500, "Volkswagen", "Polo", 2010, 250000, Fuel.PETROL, "Braga", 8, true, false);
		FilterResult r = filter.apply(l, NO_MATCH, price(false, true), COST, limits());
		assertThat(r.keep()).isTrue();
		assertThat(r.notesPt()).containsExactly("km acima do teu limite (250000 km), mas o preço compensa");
	}

	@Test
	void filter_overKmNotCheap_dropped() {
		dropped(apply(ad(1500, "Volkswagen", "Polo", 2010, 250000, Fuel.PETROL, "Braga", 8, true, false)));
	}

	@Test
	void filter_nullKm_kept() {
		assertThat(apply(ad(1500, "Volkswagen", "Polo", 2010, null, Fuel.PETROL, "Braga", 8, true, false)).keep())
				.isTrue();
	}

	@Test
	void modelsList_matchesThroughMakeAlias() {
		SearchLimits polo = limits(PriceCap.ASKING, List.of("VW Polo"), Set.of());
		assertThat(filter.apply(ok(), NO_MATCH, PLAIN, COST, polo).keep()).isTrue();
		Listing vw = ad(1500, "VW", "Polo", 2010, 150000, Fuel.PETROL, "Braga", 8, true, false);
		SearchLimits full = limits(PriceCap.ASKING, List.of("Volkswagen  POLO"), Set.of());
		assertThat(filter.apply(vw, NO_MATCH, PLAIN, COST, full).keep()).isTrue();
	}

	@Test
	void modelsList_otherModel_dropped() {
		SearchLimits yaris = limits(PriceCap.ASKING, List.of("Toyota Yaris"), Set.of());
		dropped(filter.apply(ok(), NO_MATCH, PLAIN, COST, yaris));
	}

	@Test
	void emptyModels_keepsOnlyKnownBcModels() {
		dropped(apply(ad(1500, "Opel", "Corsa", 2010, 150000, Fuel.PETROL, "Braga", 8, true, false)));
		dropped(apply(ad(1500, null, null, 2010, 150000, Fuel.PETROL, "Braga", 8, true, false)));
	}

	@Test
	void allFieldsNull_noCrash_dropReasonsGiven() {
		Listing bare = TestListings.of(new RawListing(Site.MARKETPLACE, "x", "https://example.test/x", "t", null,
				null, null, null, null, null, null, null, null, null, null, null, null, null, null, 0, null, null,
				false), false);
		dropped(filter.apply(bare, NO_MATCH, PLAIN, new RealCost(142.47, Map.of(), ""),
				limits(PriceCap.REAL_COST, List.of("VW Polo"), Set.of("Braga"))));
	}

	@Test
	void doors_fewerThanAsked_dropped() {
		FilterResult r = filter.apply(withDoors(3), NO_MATCH, PLAIN, COST, minDoors(5));
		assertThat(r.keep()).isFalse();
		assertThat(r.notesPt()).containsExactly("menos portas do que pedes (3)");
	}

	@Test
	void doors_enoughOrNoMinimum_kept() {
		assertThat(filter.apply(withDoors(5), NO_MATCH, PLAIN, COST, minDoors(5))).isEqualTo(
				new FilterResult(true, List.of()));
		assertThat(filter.apply(withDoors(3), NO_MATCH, PLAIN, COST, minDoors(null))).isEqualTo(
				new FilterResult(true, List.of()));
		assertThat(filter.apply(withDoors(null), NO_MATCH, PLAIN, COST, minDoors(null))).isEqualTo(
				new FilterResult(true, List.of()));
	}

	@Test
	void doors_unknown_keptWithNote() {
		FilterResult r = filter.apply(withDoors(null), NO_MATCH, PLAIN, COST, minDoors(5));
		assertThat(r.keep()).isTrue();
		assertThat(r.notesPt()).containsExactly("portas não indicadas");
	}
}
