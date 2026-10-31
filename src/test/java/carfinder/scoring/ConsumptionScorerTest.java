package carfinder.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import carfinder.knowledge.Generation;
import carfinder.knowledge.KnowledgeBase;
import carfinder.knowledge.KnowledgeLoader;
import carfinder.matching.CarMatch;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConsumptionScorerTest {

	private static final KnowledgeBase KB = KnowledgeLoader
			.load(Paths.get("src/test/resources/fixtures/knowledge-consumption"));
	private final ConsumptionScorer scorer = new ConsumptionScorer(KB);

	private static CarMatch match(String make, String model, String genId, String engineId) {
		Generation g = KB.generationsFor(make, model).stream().filter(x -> x.id().equals(genId)).findFirst()
				.orElseThrow();
		return new CarMatch(Optional.of(g), List.of(g), false,
				engineId == null ? Optional.empty() : KB.enginesFor(g.id()).stream()
						.filter(e -> e.id().equals(engineId)).findFirst(),
				List.of(), List.of(), false);
	}

	@Test
	void consumption_noFigure_semDadosOficiais() {
		SubScore s = scorer.score(match("Honda", "Jazz", "r-jazz-1", null));
		assertThat(s.value()).isEmpty();
		assertThat(s.confidence()).isZero();
		assertThat(s.explanationPt()).isEqualTo("sem dados oficiais");
	}

	@Test
	void consumption_noGenerationOrNoEngine_semDadosOficiais() {
		CarMatch none = new CarMatch(Optional.empty(), List.of(), false, Optional.empty(), List.of(), List.of(), false);
		assertThat(scorer.score(none).explanationPt()).isEqualTo("sem dados oficiais");
		assertThat(scorer.score(match("Renault", "Clio", "r-clio-3", null)).value()).isEmpty();
	}

	@Test
	void consumption_comparesOnlySameCycle() {
		// NEDC segment B: Clio 5.0, Fiesta 7.0. The WLTP rows (3.0 and 9.0) must not count.
		SubScore clio = scorer.score(match("Renault", "Clio", "r-clio-3", "ce1"));
		assertThat(clio.value().getAsDouble()).isCloseTo(1.0, within(1e-9));
		assertThat(clio.explanationPt()).isEqualTo("5,0 l/100 km (NEDC, oficial)");
		assertThat(clio.sourceUrls()).containsExactly("https://example.org/c1");
		assertThat(scorer.score(match("Ford", "Fiesta", "r-fiesta-6", "ce2")).value().getAsDouble())
				.isCloseTo(0.0, within(1e-9));
		SubScore yaris = scorer.score(match("Toyota", "Yaris", "r-yaris-1", "ce4"));
		assertThat(yaris.value().getAsDouble()).isCloseTo(1.0, within(1e-9));
		assertThat(yaris.explanationPt()).contains("WLTP");
	}

	@Test
	void consumption_onlyOneFigureInGroup_halfValueLowConfidence() {
		SubScore s = scorer.score(match("Volkswagen", "Golf", "r-golf-5", "ce5"));
		assertThat(s.value().getAsDouble()).isCloseTo(0.5, within(1e-9));
		assertThat(s.confidence()).isLessThan(1.0);
	}
}
