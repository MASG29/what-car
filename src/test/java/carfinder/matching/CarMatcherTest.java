package carfinder.matching;

import static carfinder.listing.TestListings.listing;
import static org.assertj.core.api.Assertions.assertThat;

import carfinder.knowledge.Engine;
import carfinder.knowledge.GearboxType;
import carfinder.knowledge.Generation;
import carfinder.knowledge.KnowledgeLoader;
import carfinder.knowledge.Tier;
import carfinder.listing.Fuel;
import carfinder.listing.Listing;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class CarMatcherTest {

	private final CarMatcher matcher = new CarMatcher(
			KnowledgeLoader.load(Paths.get("src/test/resources/fixtures/knowledge")));

	private static Listing car(String make, String model, Integer year, Integer month) {
		return listing(make, model, null, year, month, null, null, null, null);
	}

	private static Listing polo(int year, Integer month) {
		return listing("Volkswagen", "Polo", null, year, month, Fuel.PETROL, 999, 75, null);
	}

	@Test
	void singleGeneration_matched() {
		CarMatch m = matcher.match(car("Volkswagen", "Polo", 2011, 5));

		assertThat(m.generation()).map(Generation::id).hasValue("fake-polo-1");
		assertThat(m.candidates()).extracting(Generation::id).containsExactly("fake-polo-1");
		assertThat(m.mixed()).isFalse();
	}

	@Test
	void changeoverYear_mixedAndNoGeneration() {
		CarMatch m = matcher.match(car("Toyota", "Yaris", 2011, 9));

		assertThat(m.generation()).isEmpty();
		assertThat(m.candidates()).extracting(Generation::id).containsExactlyInAnyOrder("fake-yaris-1", "fake-yaris-2");
		assertThat(m.mixed()).isTrue();
	}

	@Test
	void accentedMakeModel_matches() {
		CarMatch m = matcher.match(car("Citroën", "C3", 2005, 3));

		assertThat(m.generation()).map(Generation::id).hasValue("fake-c3-1");
	}

	@Test
	void vwAlias_matches() {
		CarMatch m = matcher.match(car("VW", "Polo", 2011, 5));

		assertThat(m.generation()).map(Generation::id).hasValue("fake-polo-1");
	}

	@Test
	void engineMatchedByCcAndKw() {
		// 95 cv / 1.36 = 69.85 -> 70 kW; cc 1,298 vs 1,299
		CarMatch m = matcher.match(listing("Citroën", "C3", null, 2005, 3, Fuel.PETROL, 1298, 95, null));

		assertThat(m.engine()).map(Engine::id).hasValue("fake-eng-3");
	}

	@Test
	void ambiguousEngine_empty() {
		CarMatch m = matcher.match(listing("Citroën", "C3", null, 2005, 3, Fuel.PETROL, null, null, null));

		assertThat(m.generation()).map(Generation::id).hasValue("fake-c3-1");
		assertThat(m.engine()).isEmpty();
		assertThat(m.faults()).isEmpty();
	}

	@Test
	void engineWithUnknownCc_matchedByFuelAndKw() {
		// fake-eng-5 has no cc: the cc check is skipped, kW 51 matches 69 cv; the diesel is dropped by fuel
		CarMatch m = matcher.match(listing("Toyota", "Yaris", null, 2008, 5, Fuel.PETROL, 998, 69, null));

		assertThat(m.engine()).map(Engine::id).hasValue("fake-eng-5");
	}

	@Test
	void faultInsideWindow_hit() {
		CarMatch m = matcher.match(polo(2011, 6));

		assertThat(m.engine()).map(Engine::id).hasValue("fake-eng-1");
		assertThat(m.faults()).singleElement().satisfies(h -> {
			assertThat(h.fault().fault()).isEqualTo("Fake fault, with comma");
			assertThat(h.uncertain()).isFalse();
		});
		assertThat(m.windowEdgeUncertain()).isFalse();
	}

	@Test
	void regNearWindowEdge_uncertain() {
		// window 2010-01..2012-12, hits up to 2013-06
		CarMatch nearStart = matcher.match(polo(2010, 3));
		CarMatch nearEnd = matcher.match(polo(2013, 5));
		CarMatch after = matcher.match(polo(2013, 8));

		assertThat(nearStart.faults()).singleElement().extracting(FaultHit::uncertain).isEqualTo(true);
		assertThat(nearStart.windowEdgeUncertain()).isTrue();
		assertThat(nearEnd.faults()).singleElement().extracting(FaultHit::uncertain).isEqualTo(true);
		assertThat(after.engine()).isPresent();
		assertThat(after.faults()).isEmpty();
	}

	@Test
	void openEndedFault_hits() {
		CarMatch m = matcher.match(listing("Toyota", "Yaris", null, 2010, 6, Fuel.PETROL, 998, 69, null));

		assertThat(m.faults()).singleElement().satisfies(h -> {
			assertThat(h.fault().fault()).isEqualTo("Fake open fault");
			assertThat(h.uncertain()).isFalse();
		});
	}

	@Test
	void mmtInVersion_gearboxHit() {
		CarMatch m = matcher.match(listing("Toyota", "Yaris", "1.0 VVT-i mmt", 2008, 5, Fuel.PETROL, null, null, null));

		assertThat(m.gearbox()).singleElement().satisfies(h -> {
			assertThat(h.rule().keyword()).isEqualTo("MMT");
			assertThat(h.matchedText()).isEqualTo("1.0 VVT-i mmt");
		});
	}

	@Test
	void bSegmentAutomaticPre2012_robotisedHit() {
		CarMatch m = matcher.match(listing("VW", "Polo", null, 2011, 5, Fuel.PETROL, null, null, "Automática"));
		CarMatch y2012 = matcher.match(listing("VW", "Polo", null, 2012, 5, Fuel.PETROL, null, null, "Automática"));

		assertThat(m.gearbox()).singleElement().satisfies(h -> {
			assertThat(h.rule().type()).isEqualTo(GearboxType.ROBOTISED);
			assertThat(h.rule().tier()).isEqualTo(Tier.HIGH);
			assertThat(h.rule().sourceUrl()).isEqualTo(CarMatcher.AUTOMATIC_SOURCE);
			assertThat(h.matchedText()).isEqualTo("Automática");
		});
		assertThat(y2012.gearbox()).isEmpty();
	}

	@Test
	void automaticWithKeywordHit_noSyntheticDuplicate() {
		CarMatch m = matcher.match(listing("Toyota", "Yaris", "1.0 MMT", 2008, 5, Fuel.PETROL, null, null, "Automática"));

		assertThat(m.gearbox()).extracting(h -> h.rule().keyword()).containsExactly("MMT");
	}

	@Test
	void missingMonth_marksFaultUncertain() {
		CarMatch m = matcher.match(polo(2011, null));

		assertThat(m.faults()).singleElement().extracting(FaultHit::uncertain).isEqualTo(true);
		assertThat(m.windowEdgeUncertain()).isTrue();
	}

	@Test
	void missingMonth_faultOverlappingAnyMonthOfYear_hitsUncertain() {
		// fake-eng-3 fault window 2004-09..2004-10, hits up to 2005-04; June is outside in both years
		CarMatch lateMonths = matcher.match(listing("Citroën", "C3", null, 2004, null, Fuel.PETROL, 1298, 95, null));
		CarMatch earlyMonths = matcher.match(listing("Citroën", "C3", null, 2005, null, Fuel.PETROL, 1298, 95, null));
		CarMatch outside = matcher.match(listing("Citroën", "C3", null, 2006, null, Fuel.PETROL, 1298, 95, null));

		assertThat(lateMonths.faults()).singleElement().extracting(FaultHit::uncertain).isEqualTo(true);
		assertThat(earlyMonths.faults()).singleElement().extracting(FaultHit::uncertain).isEqualTo(true);
		assertThat(outside.engine()).map(Engine::id).hasValue("fake-eng-3");
		assertThat(outside.faults()).isEmpty();
	}

	@Test
	void knownMonthOutsideWindow_noHit() {
		CarMatch m = matcher.match(listing("Citroën", "C3", null, 2004, 6, Fuel.PETROL, 1298, 95, null));

		assertThat(m.faults()).isEmpty();
	}

	@Test
	void unknownCcKwEngines_doNotHideEngineMatchedOnKnownValues() {
		CarMatcher real = new CarMatcher(KnowledgeLoader.loadClasspath());

		CarMatch polo = real.match(listing("Volkswagen", "Polo", null, 2006, 5, Fuel.PETROL, 1198, 64, null));
		CarMatch yaris = real.match(listing("Toyota", "Yaris", null, 2008, 5, Fuel.PETROL, 998, 69, null));

		assertThat(polo.engine()).map(Engine::id).hasValue("vw-1.2-3cyl");
		assertThat(yaris.engine()).map(Engine::id).hasValue("toyota-1kr");
	}

	@Test
	void missingFields_emptyMatchNoCrash() {
		CarMatch m = matcher.match(listing(null, null, null, null, null, null, null, null, null));

		assertThat(m.generation()).isEmpty();
		assertThat(m.candidates()).isEmpty();
		assertThat(m.engine()).isEmpty();
		assertThat(m.gearbox()).isEmpty();
	}

	@Test
	void realKnowledgeBase_accentsAndAliases() {
		CarMatcher real = new CarMatcher(KnowledgeLoader.loadClasspath());

		CarMatch c3 = real.match(car("CITROEN", "c3", 2005, 4));
		CarMatch polo = real.match(car("VW", "Polo", 2006, 4));

		assertThat(c3.generation()).map(Generation::id).hasValue("c3-i");
		assertThat(c3.mixed()).isFalse();
		assertThat(polo.generation()).map(Generation::id).hasValue("polo-9n");
		assertThat(polo.mixed()).isFalse();
	}

	@Test
	void realKnowledgeBase_kwFills_pickOnlyTheEngineTheyIdentify() {
		CarMatcher real = new CarMatcher(KnowledgeLoader.loadClasspath());

		CarMatch tu3 = real.match(listing("Peugeot", "206", null, 2005, 5, Fuel.PETROL, 1360, 75, null));
		CarMatch sixteenValve = real.match(listing("Peugeot", "206", null, 2005, 5, Fuel.PETROL, 1360, 90, null));
		CarMatch peugeot16 = real.match(listing("Peugeot", "206", null, 2000, 5, Fuel.PETROL, 1587, 90, null));
		CarMatch golf16 = real.match(listing("Volkswagen", "Golf", null, 2005, 5, Fuel.PETROL, 1595, 102, null));

		assertThat(tu3.engine()).map(Engine::id).hasValue("psa-tu3");
		// KFU and TU5 stay without kW: a 1.6 8v at about 65 kW would otherwise pass as the 1.4 16v
		assertThat(sixteenValve.engine()).isEmpty();
		assertThat(peugeot16.engine()).isEmpty();
		assertThat(golf16.engine()).isEmpty(); // 75 kW, not the 1.4 16v (55/59 kW)
	}

	@Test
	void realKnowledgeBase_neighbourGenerations() {
		CarMatcher real = new CarMatcher(KnowledgeLoader.loadClasspath());

		CarMatch polo = real.match(listing("VW", "Polo", null, 2011, 4, Fuel.PETROL, 1198, 60, null));
		CarMatch golf = real.match(car("Volkswagen", "Golf", 2010, 4));
		CarMatch variant = real.match(car("Volkswagen", "Golf Variant", 2005, 4));
		CarMatch evo = real.match(car("Fiat", "Punto Evo", 2011, 4));

		assertThat(polo.generation()).map(Generation::id).hasValue("polo-6r");
		assertThat(polo.engine()).map(Engine::id).hasValue("vw-1.2-3cyl");
		assertThat(golf.generation()).map(Generation::id).hasValue("golf-vi");
		assertThat(variant.generation()).map(Generation::id).hasValue("golf-iv-variant");
		assertThat(evo.generation()).map(Generation::id).hasValue("punto-evo");
	}

	@Test
	void realKnowledgeBase_clioCampusIsClioII_otherClios2005to2012Mixed() {
		CarMatcher real = new CarMatcher(KnowledgeLoader.loadClasspath());

		CarMatch campus = real.match(listing("Renault", "Clio", "1.2 Campus", 2007, 4, Fuel.PETROL, null, null, null));
		CarMatch dci = real.match(listing("Renault", "Clio", "1.5 dCi", 2007, 4, Fuel.DIESEL, null, null, null));

		assertThat(campus.generation()).map(Generation::id).hasValue("clio-ii");
		assertThat(campus.mixed()).isFalse();
		assertThat(dci.generation()).isEmpty();
		assertThat(dci.mixed()).isTrue();
		assertThat(dci.candidates()).extracting(Generation::id).containsExactlyInAnyOrder("clio-ii", "clio-iii");
	}
}
