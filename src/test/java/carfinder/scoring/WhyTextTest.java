package carfinder.scoring;

import static carfinder.scoring.ScoringServiceTest.sub;
import static carfinder.scoring.ScoringServiceTest.subs;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WhyTextTest {

	static ScoredListing scored(Map<Criterion, SubScore> present, double confidence, ConditionResult condition) {
		return new ScoredListing(null, List.of(), subs(present), 0.7, confidence, 0.5, false, false, List.of(), List.of(),
				null, null, condition, null);
	}

	private static final ConditionResult NONE = new ConditionResult(List.of(), List.of());

	@Test
	void twoStrongest_semDados_confidence_adjustment() {
		ScoredListing s = scored(Map.of(Criterion.RELIABILITY, sub(0.8), Criterion.PRICE, sub(0.7),
				Criterion.COMPLETENESS, sub(0.6), Criterion.PHOTOS, sub(0.5)), 0.47,
				new ConditionResult(List.of("riscos"), List.of()));
		assertThat(WhyText.build(s, 3)).isEqualTo(
				"3.º lugar: fiabilidade 80 % e preço 70 %; consumo sem dados; confiança 47 %; "
						+ "−0,05 por riscos mencionados.");
	}

	@Test
	void noAdjustment_noMissing_leftOut() {
		ScoredListing s = scored(Map.of(Criterion.PRICE, sub(0.9), Criterion.RELIABILITY, sub(0.85),
				Criterion.CONSUMPTION, sub(0.5), Criterion.COMPLETENESS, sub(0.6), Criterion.PHOTOS, sub(0.4)), 1.0,
				NONE);
		assertThat(WhyText.build(s, 1)).isEqualTo("1.º lugar: preço 90 % e fiabilidade 85 %; "
				+ "confiança 100 %.");
	}

	@Test
	void severalMissing_andBothKindsOfHints() {
		ScoredListing s = scored(Map.of(Criterion.PRICE, sub(0.6)), 0.3,
				new ConditionResult(List.of("riscos", "ferrugem"), List.of("embraiagem")));
		assertThat(WhyText.build(s, 2)).isEqualTo("2.º lugar: preço 60 %; "
				+ "fiabilidade, consumo, anúncio completo, fotos sem dados; confiança 30 %; "
				+ "−0,07 por riscos, ferrugem mencionados e manutenção recente (embraiagem).");
	}

	@Test
	void maintenanceOnly_positive() {
		ScoredListing s = scored(Map.of(Criterion.PRICE, sub(0.6), Criterion.RELIABILITY, sub(0.6),
				Criterion.CONSUMPTION, sub(0.6), Criterion.COMPLETENESS, sub(0.6), Criterion.PHOTOS, sub(0.6)), 1.0,
				new ConditionResult(List.of(), List.of("embraiagem", "amortecedores")));
		assertThat(WhyText.build(s, 4)).endsWith("; +0,06 por manutenção recente (embraiagem, amortecedores).");
	}

	@Test
	void damage_mentionedWithPenalty() {
		ScoredListing s = scored(Map.of(Criterion.PRICE, sub(0.6), Criterion.RELIABILITY, sub(0.6),
				Criterion.CONSUMPTION, sub(0.6), Criterion.COMPLETENESS, sub(0.6), Criterion.PHOTOS, sub(0.6)), 1.0,
				new ConditionResult(List.of("riscos"), List.of(), List.of("batido", "avariado"), false));
		assertThat(WhyText.build(s, 2)).endsWith("; −0,20 por riscos mencionados e precisa de reparação "
				+ "(batido, avariado).");
	}
}
