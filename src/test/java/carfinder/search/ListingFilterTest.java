package carfinder.search;

import static org.assertj.core.api.Assertions.assertThat;

import carfinder.buying.RealCost;
import carfinder.geo.Places;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ListingFilterTest {

	private final ListingFilter filter = new ListingFilter(
			KnowledgeLoader.load(Paths.get("src/test/resources/fixtures/knowledge")),
			new Places(Paths.get("src/test/resources/fixtures/geo/places-test.tsv")));

	private static final CarMatch NO_MATCH = new CarMatch(Optional.empty(), List.of(), false, Optional.empty(),
			List.of(), List.of(), false);
	private static final PriceResult PLAIN = price(false, false);
	private static final RealCost COST = new RealCost(1800, Map.of(), "");

	static PriceResult price(boolean inflated, boolean wellBelow) {
		return new PriceResult(new SubScore(OptionalDouble.empty(), 0, "", List.of(), List.of()),
				OptionalDouble.empty(), inflated, wellBelow);
	}

	static SearchLimits limits(PriceCap cap, List<String> models, String centre) {
		return new SearchLimits(2500, cap, 200000, 2005, EnumSet.of(Fuel.PETROL, Fuel.DIESEL), centre,
				centre == null ? null : 65, models, RealCostSettings.defaults(), null, null);
	}

	static SearchLimits minDoors(Integer n) {
		return new SearchLimits(2500, PriceCap.ASKING, 200000, 2005, EnumSet.of(Fuel.PETROL, Fuel.DIESEL), null,
				null, List.of(), RealCostSettings.defaults(), n, null);
	}

	static Listing withDoors(Integer doors) {
		return TestListings.of(new RawListing(Site.STANDVIRTUAL, "x", "https://example.test/x", "t", 1500,
				"Volkswagen", "Polo", null, 2010, 6, 150000, Fuel.PETROL, null, null, null, doors, null, "Braga", null,
				8, null, null, false), true);
	}

	static SearchLimits limits() {
		return limits(PriceCap.ASKING, List.of(), null);
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
	void detailsWithoutPhotoCount_notDroppedAsSemFotos() {
		Listing marketplace = TestListings.of(new RawListing(Site.MARKETPLACE, "m", "https://example.test/m", "t",
				1500, "Volkswagen", "Polo", null, 2010, null, 150000, Fuel.PETROL, null, null, null, null, null,
				"Braga", null, 0, "Bom estado.", null, false), true);
		Listing standvirtual = ad(1500, "Volkswagen", "Polo", 2010, 150000, Fuel.PETROL, "Braga", 0, true, false);

		assertThat(apply(marketplace).keep()).isTrue();
		assertThat(apply(standvirtual).notesPt()).contains("sem fotos");
	}

	@Test
	void plainAd_kept() {
		FilterResult r = apply(ok());
		assertThat(r.keep()).isTrue();
		assertThat(r.notesPt()).isEmpty();
	}

	@Test
	void unavailable_dropped() {
		FilterResult r = apply(TestListings.unavailable(ok()));
		assertThat(r.keep()).isFalse();
		assertThat(r.notesPt()).contains("vendido ou reservado");
	}

	@Test
	void custojustoWithoutRecognisedModel_saysSo() {
		Listing l = TestListings.of(new RawListing(Site.CUSTOJUSTO, "c", "https://www.custojusto.pt/a/veiculos/carros-usados/b/c-1",
				"Carro antigo", 1500, null, null, null, 2008, null, null, null, null, null, null, null, null,
				null, null, 0, null, null, false), false);

		assertThat(apply(l).notesPt()).contains("marca ou modelo não reconhecidos no título");
	}

	@Test
	void marketplaceWithoutRecognisedModel_saysSo() {
		Listing l = TestListings.of(new RawListing(Site.MARKETPLACE, "m", "https://www.facebook.com/marketplace/item/1/",
				"2008 Opel Corsa 1.2", 1500, null, null, null, 2008, null, null, null, null, null, null, null, null,
				null, null, 0, null, null, false), false);

		assertThat(apply(l).notesPt()).contains("marca ou modelo não reconhecidos no título");
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
		SearchLimits real = limits(PriceCap.REAL_COST, List.of(), null);
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

	private static Listing at(String city, String district) {
		return TestListings.of(new RawListing(Site.STANDVIRTUAL, "x", "https://example.test/x", "t", 1500,
				"Volkswagen", "Polo", null, 2010, 6, 150000, Fuel.PETROL, null, null, null, null, null, district,
				city, 8, null, null, false), true);
	}

	private FilterResult near(Listing l) {
		return filter.apply(l, NO_MATCH, PLAIN, COST, limits(PriceCap.ASKING, List.of(), "Ponte de Lima"));
	}

	@Test
	void location_cityInsideRadius_keptWithDistance() {
		FilterResult r = near(at("Braga", "Braga"));
		assertThat(r.keep()).isTrue();
		assertThat(r.notesPt()).containsExactly("a ~27 km em linha reta");
	}

	@Test
	void location_cityOutsideRadius_dropped() {
		FilterResult r = near(at("Lisboa", "Lisboa"));
		assertThat(r.keep()).isFalse();
		assertThat(r.notesPt()).singleElement().asString()
				.matches("a ~\\d+ km de Ponte de Lima \\(raio 65 km\\)");
	}

	@Test
	void location_districtOnly_usesCapital() {
		FilterResult braga = near(at(null, "Braga"));
		assertThat(braga.keep()).isTrue();
		assertThat(braga.notesPt()).containsExactly("só sabemos o distrito (Braga)", "a ~27 km em linha reta");
		FilterResult porto = near(at(null, "Porto"));
		assertThat(porto.keep()).isFalse();
		assertThat(porto.notesPt()).containsExactly("a ~69 km de Ponte de Lima (raio 65 km)");
	}

	@Test
	void location_ambiguousOrUnknown_keptWithNote() {
		FilterResult amb = near(at("Porto", null));
		assertThat(amb.keep()).isTrue();
		assertThat(amb.notesPt()).containsExactly("localidade ambígua (Porto)");
		FilterResult unknown = near(at(null, null));
		assertThat(unknown.keep()).isTrue();
		assertThat(unknown.notesPt()).containsExactly("localização desconhecida");
	}

	@Test
	void location_nullCentre_noLocationNote() {
		FilterResult r = apply(at("Lisboa", "Lisboa"));
		assertThat(r.keep()).isTrue();
		assertThat(r.notesPt()).isEmpty();
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
		SearchLimits polo = limits(PriceCap.ASKING, List.of("VW Polo"), null);
		assertThat(filter.apply(ok(), NO_MATCH, PLAIN, COST, polo).keep()).isTrue();
		Listing vw = ad(1500, "VW", "Polo", 2010, 150000, Fuel.PETROL, "Braga", 8, true, false);
		SearchLimits full = limits(PriceCap.ASKING, List.of("Volkswagen  POLO"), null);
		assertThat(filter.apply(vw, NO_MATCH, PLAIN, COST, full).keep()).isTrue();
	}

	@Test
	void modelsList_otherModel_dropped() {
		SearchLimits yaris = limits(PriceCap.ASKING, List.of("Toyota Yaris"), null);
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
				limits(PriceCap.REAL_COST, List.of("VW Polo"), "Braga")));
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

	private static Listing saying(String title, String description) {
		return TestListings.of(new RawListing(Site.STANDVIRTUAL, "x", "https://example.test/x", title, 1500,
				"Volkswagen", "Polo", null, 2010, 6, 150000, Fuel.PETROL, null, null, null, null, null, "Braga", null,
				8, description, null, false), true);
	}

	private static SearchLimits showDamaged(Boolean show) {
		return new SearchLimits(2500, PriceCap.ASKING, 200000, 2005, EnumSet.of(Fuel.PETROL, Fuel.DIESEL), null, null,
				List.of(), RealCostSettings.defaults(), null, show);
	}

	@Test
	void partsInTitleOrDescription_dropped_withTheSameTextAsIsParts() {
		FilterResult title = apply(saying("Polo para peças", null));
		assertThat(title.keep()).isFalse();
		assertThat(title.notesPt()).containsExactly("anúncio de peças");
		assertThat(apply(saying("Polo", "Vende-se para peças.")).notesPt()).containsExactly("anúncio de peças");
		assertThat(apply(ad(1500, "Volkswagen", "Polo", 2010, 150000, Fuel.PETROL, "Braga", 8, true, true))
				.notesPt()).containsExactly("anúncio de peças");
		assertThat(apply(saying("Polo", "Não é para peças.")).keep()).isTrue();
	}

	@Test
	void parts_droppedEvenWhenDamagedAdsAreShown() {
		FilterResult r = filter.apply(saying("Polo para peças", null), NO_MATCH, PLAIN, COST, showDamaged(true));
		assertThat(r.keep()).isFalse();
		assertThat(r.notesPt()).containsExactly("anúncio de peças");
	}

	@Test
	void damage_droppedByDefault_withReasonListingTheLabels() {
		for (Boolean show : new Boolean[] { null, false }) {
			FilterResult r = filter.apply(saying("Polo", "Motor gripado e caixa partida."), NO_MATCH, PLAIN, COST,
					showDamaged(show));
			assertThat(r.keep()).isFalse();
			assertThat(r.notesPt()).containsExactly("precisa de reparação (motor gripado, caixa partida)");
		}
		assertThat(apply(saying("Polo avariado", null)).notesPt())
				.containsExactly("precisa de reparação (avariado)");
	}

	@Test
	void damage_keptWhenShown_withRepairCostNote() {
		FilterResult r = filter.apply(saying("Polo", "O carro não pega."), NO_MATCH, PLAIN, COST, showDamaged(true));
		assertThat(r.keep()).isTrue();
		assertThat(r.notesPt()).containsExactly("custo da reparação: sem dados");
	}

	@Test
	void damage_negated_notDropped() {
		assertThat(apply(saying("Polo", "Nunca foi batido.")).keep()).isTrue();
	}
}
