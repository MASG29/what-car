package carfinder.buying;

import static org.assertj.core.api.Assertions.assertThat;

import carfinder.knowledge.Engine;
import carfinder.knowledge.Timing;
import carfinder.listing.Fuel;
import carfinder.listing.Listing;
import carfinder.listing.RawListing;
import carfinder.listing.Site;
import carfinder.listing.TestListings;
import carfinder.matching.CarMatch;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RealCostCalculatorTest {

	private final RealCostCalculator calc = new RealCostCalculator();
	private final RealCostSettings s = RealCostSettings.defaults();

	static Listing ad(Integer year, Integer month, String description) {
		return ad(1500, year, month, description);
	}

	static Listing ad(Integer price, Integer year, Integer month, String description) {
		return TestListings.of(new RawListing(Site.STANDVIRTUAL, "t", "https://example.test/1", "t", price, "Toyota",
				"Yaris", null, year, month, 150000, Fuel.PETROL, 998, 68, "manual", 5, null, null, null, 0,
				description, null, false), true);
	}

	static CarMatch match(Timing timing) {
		Engine e = new Engine("e", "toyota", "1sz", "PETROL", 998, List.of(50), timing);
		return new CarMatch(Optional.empty(), List.of(), false, Optional.of(e), List.of(), List.of(), false);
	}

	static CarMatch noEngine() {
		return new CarMatch(Optional.empty(), List.of(), false, Optional.empty(), List.of(), List.of(), false);
	}

	@Test
	void defaults_matchReport() {
		assertThat(s).isEqualTo(new RealCostSettings(105, 37.47, 40, 149, 350, 800, 1500));
	}

	@Test
	void pre2007_usesLowIuc() {
		RealCost c = calc.estimate(ad(2005, 3, null), match(Timing.CHAIN), s);
		assertThat(c.itemsPt()).containsEntry("IUC (estimativa)", 40.0);
		assertThat(c.totalEur()).isEqualTo(1500 + 105 + 37.47 + 40);
	}

	@Test
	void post2007_usesHighIuc() {
		RealCost c = calc.estimate(ad(2010, 1, null), match(Timing.CHAIN), s);
		assertThat(c.itemsPt()).containsEntry("IUC (estimativa)", 149.0);
	}

	@Test
	void july2007_isPost_june2007_isPre() {
		assertThat(calc.estimate(ad(2007, 7, null), match(Timing.CHAIN), s).itemsPt())
				.containsEntry("IUC (estimativa)", 149.0);
		assertThat(calc.estimate(ad(2007, 6, null), match(Timing.CHAIN), s).itemsPt())
				.containsEntry("IUC (estimativa)", 40.0);
	}

	@Test
	void year2007_monthUnknown_usesHigherAndLabels() {
		RealCost c = calc.estimate(ad(2007, null, null), match(Timing.CHAIN), s);
		assertThat(c.itemsPt()).containsEntry("IUC (estimativa, mês desconhecido)", 149.0)
				.doesNotContainKey("IUC (estimativa)");
		assertThat(c.totalEur()).isEqualTo(1500 + 105 + 37.47 + 149);
	}

	@Test
	void yearUnknown_iucLeftOutOfTotalAndSaid() {
		RealCost c = calc.estimate(ad(null, null, null), match(Timing.CHAIN), s);
		assertThat(c.itemsPt()).containsEntry("IUC (fora do total: ano de matrícula desconhecido)", 0.0);
		assertThat(c.totalEur()).isEqualTo(1500 + 105 + 37.47);
	}

	@Test
	void beltEngineWithoutMention_addsBelt() {
		RealCost c = calc.estimate(ad(2005, 3, "Carro em bom estado."), match(Timing.BELT), s);
		assertThat(c.itemsPt()).containsEntry("Correia (assumida por fazer)", 350.0);
		assertThat(c.totalEur()).isEqualTo(1500 + 105 + 37.47 + 40 + 350);
	}

	@Test
	void unknownTimingOrNoEngine_addsBelt() {
		assertThat(calc.estimate(ad(2005, 3, null), match(Timing.UNKNOWN), s).itemsPt())
				.containsKey("Correia (assumida por fazer)");
		assertThat(calc.estimate(ad(2005, 3, null), noEngine(), s).itemsPt())
				.containsKey("Correia (assumida por fazer)");
	}

	@Test
	void chainEngine_noBelt() {
		assertThat(calc.estimate(ad(2005, 3, null), match(Timing.CHAIN), s).itemsPt())
				.doesNotContainKey("Correia (assumida por fazer)");
	}

	@Test
	void descriptionMentionsCorreia_noBelt() {
		assertThat(calc.estimate(ad(2005, 3, "Correia de distribuição trocada em 2024"), match(Timing.BELT), s)
				.itemsPt()).doesNotContainKey("Correia (assumida por fazer)");
		assertThat(calc.estimate(ad(2005, 3, "Kit de DISTRIBUIÇÃO novo"), match(Timing.BELT), s).itemsPt())
				.doesNotContainKey("Correia (assumida por fazer)");
	}

	@Test
	void insuranceNotInTotal() {
		RealCost c = calc.estimate(ad(2005, 3, null), match(Timing.CHAIN), s);
		assertThat(c.totalEur()).isEqualTo(1500 + 105 + 37.47 + 40);
		assertThat(c.insuranceRangePt()).contains("800").contains("1500").contains("€").doesNotContain("EUR");
		assertThat(c.itemsPt().keySet()).noneMatch(k -> k.toLowerCase().contains("seguro"));
	}

	@Test
	void itemsKeepStableOrder() {
		RealCost c = calc.estimate(ad(2005, 3, null), match(Timing.BELT), s);
		assertThat(c.itemsPt().keySet()).containsExactly("Preço pedido", "Papelada", "Inspeção", "IUC (estimativa)",
				"Correia (assumida por fazer)");
	}

	@Test
	void askingPrice_isFirstItemAndInTotal() {
		RealCost c = calc.estimate(ad(2005, 3, null), match(Timing.CHAIN), s);
		assertThat(c.itemsPt()).containsEntry("Preço pedido", 1500.0);
		assertThat(c.itemsPt().keySet().iterator().next()).isEqualTo("Preço pedido");
	}

	@Test
	void unknownPrice_leftOutOfTotalAndSaid() {
		RealCost c = calc.estimate(ad(null, 2005, 3, null), match(Timing.CHAIN), s);
		assertThat(c.itemsPt()).containsEntry("Preço pedido (desconhecido)", 0.0).doesNotContainKey("Preço pedido");
		assertThat(c.itemsPt().keySet().iterator().next()).isEqualTo("Preço pedido (desconhecido)");
		assertThat(c.totalEur()).isEqualTo(105 + 37.47 + 40);
	}
}
