package carfinder.scoring;

import static carfinder.listing.TestListings.listing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import carfinder.knowledge.EngineFault;
import carfinder.knowledge.GearboxRule;
import carfinder.knowledge.GearboxType;
import carfinder.knowledge.Generation;
import carfinder.knowledge.KnowledgeBase;
import carfinder.knowledge.KnowledgeLoader;
import carfinder.knowledge.Tier;
import carfinder.listing.Fuel;
import carfinder.listing.Listing;
import carfinder.matching.CarMatch;
import carfinder.matching.CarMatcher;
import carfinder.matching.FaultHit;
import carfinder.matching.GearboxHit;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReliabilityScorerTest {

	private static final KnowledgeBase KB = KnowledgeLoader
			.load(Paths.get("src/test/resources/fixtures/knowledge-reliability"));
	private final ReliabilityScorer scorer = new ReliabilityScorer(KB);

	private static final FaultHit HIGH_FAULT = fault(Tier.HIGH, false);

	private static Generation gen(String make, String model, String id) {
		return KB.generationsFor(make, model).stream().filter(g -> g.id().equals(id)).findFirst().orElseThrow();
	}

	private static CarMatch exact(Generation g, List<FaultHit> faults, List<GearboxHit> gearbox) {
		return new CarMatch(Optional.of(g), List.of(g), false, Optional.empty(), faults, gearbox,
				faults.stream().anyMatch(FaultHit::uncertain));
	}

	private static CarMatch exact(Generation g) {
		return exact(g, List.of(), List.of());
	}

	private static FaultHit fault(Tier tier, boolean uncertain) {
		return new FaultHit(new EngineFault("e1", "Corrente de distribuição estica", null, null, tier,
				"Ouve o motor a frio", "https://example.org/fault", "F"), uncertain);
	}

	private static Listing car(String make, String model, Integer year) {
		return listing(make, model, null, year, 6, Fuel.PETROL, null, null, null);
	}

	private SubScore clio3AgeTen(List<FaultHit> faults, List<GearboxHit> gearbox) {
		return scorer.score(exact(gen("Renault", "Clio", "r-clio-3"), faults, gearbox), car("Renault", "Clio", 2016),
				10);
	}

	@Test
	void ratioOne_isHalf() {
		assertThat(Ratio.toScore(1.0)).isEqualTo(0.5);
		assertThat(Ratio.toScore(0.4)).isEqualTo(1.0);
		assertThat(Ratio.toScore(2.0)).isEqualTo(0.0);
	}

	@Test
	void exactAgeBand_confidenceOne() {
		// TUV 10-11 years: 28.0 vs reference 28.0
		SubScore s = clio3AgeTen(List.of(), List.of());

		assertThat(s.value()).hasValue(0.5);
		assertThat(s.confidence()).isEqualTo(1.0);
		assertThat(s.explanationPt()).contains("TÜV 2019");
		assertThat(s.sourceUrls()).containsExactly("https://example.org/tuv-clio3-10");
	}

	@Test
	void olderThanSurveys_usesOldestRow_withUltimoDadoText() {
		// 15 years: no band covers it; oldest row is 12-13 years, 35.0 vs 28.0 -> r 1.25
		SubScore s = scorer.score(exact(gen("Renault", "Clio", "r-clio-3")), car("Renault", "Clio", 2011), 15);

		assertThat(s.value().getAsDouble()).isCloseTo(0.25, within(1e-9));
		assertThat(s.confidence()).isEqualTo(0.7);
		assertThat(s.explanationPt()).isEqualTo("último dado: TÜV 2024, aos ~12 anos");
	}

	@Test
	void seriesProxy_flaggedAproximado() {
		// Clio II 2002 -> ADAC EZ2004 (50 vs 40); EZ2006 is Clio III only
		SubScore s = scorer.score(exact(gen("Renault", "Clio", "r-clio-2")), car("Renault", "Clio", 2002), 24);

		assertThat(s.value().getAsDouble()).isCloseTo(0.25, within(1e-9));
		assertThat(s.confidence()).isEqualTo(0.5);
		assertThat(s.explanationPt()).isEqualTo("aproximado: Renault Clio ADAC EZ2004, aos ~9 anos");
		assertThat(s.sourceUrls()).containsExactly("https://example.org/adac-clio-2004");
	}

	@Test
	void seriesRowOfOtherGeneration_excluded() {
		// Fiesta Mk5 2007: EZ2009 (Mk6 only) is nearer than EZ2004 but belongs to another generation
		SubScore s = scorer.score(exact(gen("Ford", "Fiesta", "r-fiesta-5")), car("Ford", "Fiesta", 2007), 19);

		assertThat(s.value()).hasValue(1.0);
		assertThat(s.sourceUrls()).containsExactly("https://example.org/adac-fiesta-2004");
		assertThat(s.explanationPt()).contains("EZ2004").contains("(linha combinada)");
	}

	@Test
	void seriesRowOutsideCandidateWindow_excluded() {
		// C3 I ends 2009-06 and its successor is not in the knowledge base: EZ2010 must not score a 2009 car
		Generation c3 = gen("Citroën", "C3", "r-c3-1");
		CarMatch mixed = new CarMatch(Optional.empty(), List.of(c3), true, Optional.empty(), List.of(), List.of(),
				false);

		SubScore s = scorer.score(mixed, car("Citroën", "C3", 2009), 17);

		assertThat(s.value()).hasValue(0.5);
		assertThat(s.sourceUrls()).containsExactly("https://example.org/adac-c3-2007");
		assertThat(s.confidence()).isCloseTo(0.4, within(1e-9));
	}

	@Test
	void noRows_engineFaultsOnly_valueEmpty() {
		SubScore s = scorer.score(exact(gen("Honda", "Jazz", "r-jazz-1"), List.of(HIGH_FAULT), List.of()),
				car("Honda", "Jazz", 2005), 21);

		assertThat(s.value()).isEmpty();
		assertThat(s.confidence()).isEqualTo(0.2);
		assertThat(s.explanationPt()).contains("Corrente de distribuição estica");
		assertThat(s.warningsPt()).anySatisfy(w -> assertThat(w).contains("alto").contains("Ouve o motor a frio"));
		assertThat(s.sourceUrls()).containsExactly("https://example.org/fault");
	}

	@Test
	void nothing_semDados_confidenceZero() {
		// Jazz has only a row without a measured age: it cannot be shown, so it is skipped
		SubScore s = scorer.score(exact(gen("Honda", "Jazz", "r-jazz-1")), car("Honda", "Jazz", 2005), 21);

		assertThat(s.value()).isEmpty();
		assertThat(s.confidence()).isEqualTo(0.0);
		assertThat(s.explanationPt()).isEqualTo("sem dados");
		assertThat(s.warningsPt()).isEmpty();
	}

	@Test
	void unknownCarAndYear_semDadosNoCrash() {
		CarMatch none = new CarMatch(Optional.empty(), List.of(), false, Optional.empty(), List.of(), List.of(),
				false);

		SubScore s = scorer.score(none, listing(null, null, null, null, null, null, null, null, null), 0);

		assertThat(s.value()).isEmpty();
		assertThat(s.explanationPt()).isEqualTo("sem dados");
	}

	@Test
	void batteryCause_excludedAndWarns() {
		// EZ2006 is nearest but caused by the 12 V battery: EZ2008 (40 vs 40) scores instead
		SubScore s = scorer.score(exact(gen("Toyota", "Yaris", "r-yaris-1")), car("Toyota", "Yaris", 2006), 20);

		assertThat(s.value()).hasValue(0.5);
		assertThat(s.explanationPt()).contains("EZ2008").doesNotContain("EZ2006");
		assertThat(s.warningsPt()).contains("Verifica a bateria de 12 V");
	}

	@Test
	void highFault_penalises() {
		assertThat(clio3AgeTen(List.of(HIGH_FAULT), List.of()).value().getAsDouble()).isCloseTo(0.2, within(1e-9));
		assertThat(clio3AgeTen(List.of(fault(Tier.MEDIUM, false)), List.of()).value().getAsDouble())
				.isCloseTo(0.35, within(1e-9));
		assertThat(clio3AgeTen(List.of(fault(Tier.LOW, false)), List.of()).value()).hasValue(0.5);
		assertThat(clio3AgeTen(List.of(HIGH_FAULT, HIGH_FAULT), List.of()).value()).hasValue(0.0);
	}

	@Test
	void syntheticGearboxHit_penalisesAndWarns() {
		GearboxRule automatic = new GearboxRule("autom", GearboxType.ROBOTISED, Tier.HIGH,
				"reports/Fiabilidade carros segmentos B e C.md", "I");

		SubScore s = clio3AgeTen(List.of(), List.of(new GearboxHit(automatic, "Automática")));

		assertThat(s.value().getAsDouble()).isCloseTo(0.2, within(1e-9));
		assertThat(s.confidence()).isEqualTo(1.0);
		assertThat(s.warningsPt()).anySatisfy(w -> assertThat(w).contains("robotizada").contains("Automática"));
	}

	@Test
	void uncertainFaultWindow_asksForVin() {
		SubScore s = clio3AgeTen(List.of(fault(Tier.MEDIUM, true)), List.of());

		assertThat(s.warningsPt()).contains("Pede o VIN ou o código do motor para confirmar");
	}

	@Test
	void snippetTag_lowersConfidence() {
		SubScore s = scorer.score(exact(gen("VW", "Polo", "r-polo-1")), car("VW", "Polo", 2018), 8);

		assertThat(s.value()).hasValue(0.5);
		assertThat(s.confidence()).isCloseTo(0.8, within(1e-9));
	}

	@Test
	void neverUsesOtherGenerationRows() {
		// a Clio III TUV row covers age 10, but this is a Clio II
		SubScore s = scorer.score(exact(gen("Renault", "Clio", "r-clio-2")), car("Renault", "Clio", 2002), 10);

		assertThat(s.sourceUrls()).noneMatch(u -> u.contains("tuv-clio3"));
		assertThat(s.explanationPt()).startsWith("aproximado:");
	}

	@Test
	void realKnowledgeBase_c3Of2009_doesNotUseSuccessorRow() {
		KnowledgeBase real = KnowledgeLoader.loadClasspath();
		Listing c3 = car("Citroën", "C3", 2009);

		SubScore s = new ReliabilityScorer(real).score(new CarMatcher(real).match(c3), c3, 17);

		assertThat(s.explanationPt()).contains("EZ2007").doesNotContain("EZ2010");
		assertThat(s.confidence()).isCloseTo(0.4, within(1e-9));
	}
}
