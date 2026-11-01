package carfinder.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConditionReaderTest {

	@Test
	void nullOrBlank_noHints() {
		assertThat(ConditionReader.read(null)).isEqualTo(new ConditionResult(List.of(), List.of()));
		assertThat(ConditionReader.read("  ")).isEqualTo(new ConditionResult(List.of(), List.of()));
	}

	@Test
	void problems_eachGroupFound_accentsAndCaseIgnored() {
		ConditionResult r = ConditionReader.read("Tem RISCOS na porta, uma amolgadela, pintura a descascar no tejadilho, "
				+ "alguma ferrugem, embraiagem a patinar, luz do airbag acesa, luz do motor, deita fumo, "
				+ "barulho no motor e caixa a raspar.");
		assertThat(r.problemsPt()).containsExactly("riscos", "amolgadelas",
				"pintura danificada", "ferrugem",
				"embraiagem gasta", "luz do airbag",
				"luz do motor", "fumo",
				"barulho no motor", "caixa com problemas");
		assertThat(r.maintenancePt()).isEmpty();
	}

	@Test
	void problems_alternativeWordings() {
		assertThat(ConditionReader.read("Um risco pequeno; carro amolgado; pintura queimada; airbag acesa; "
				+ "embraiagem gasta; caixa com folga").problemsPt()).containsExactly("riscos",
						"amolgadelas", "pintura danificada",
						"embraiagem gasta", "luz do airbag",
						"caixa com problemas");
	}

	@Test
	void problems_samePhraseTwice_countedOnce() {
		assertThat(ConditionReader.read("riscos na frente e riscos atrás").problemsPt())
				.containsExactly("riscos");
	}

	@Test
	void maintenance_eachGroupFound() {
		ConditionResult r = ConditionReader.read("Embraiagem nova, bomba de água substituída, amortecedores trocados "
				+ "e correia de distribuição feita.");
		assertThat(r.maintenancePt()).containsExactly("embraiagem",
				"bomba de água", "amortecedores",
				"distribuição");
		assertThat(r.problemsPt()).isEmpty();
	}

	@Test
	void maintenance_kitStillDue_notCounted() {
		assertThat(ConditionReader.read("Precisa de kit de distribuição.").maintenancePt()).isEmpty();
	}

	@Test
	void maintenance_kitAndBeltTogether_countedOnce() {
		assertThat(ConditionReader.read("Kit de distribuição e correia nova").maintenancePt())
				.containsExactly("distribuição");
	}

	@ParameterizedTest
	@ValueSource(strings = { "Sem riscos nem amolgadelas", "Nunca teve ferrugem", "Não tem riscos",
			"Zero problemas de ferrugem", "não tem qualquer ferrugem", "Sem luz do motor acesa",
			"NÃO deita fumo" })
	void negation_beforePhraseInSameClause_ignored(String text) {
		assertThat(ConditionReader.read(text).problemsPt()).isEmpty();
	}

	@Test
	void negation_coversListJoinedByCommaOrEOuNem() {
		assertThat(ConditionReader.read("Sem riscos, amolgadelas ou ferrugem").problemsPt()).isEmpty();
		assertThat(ConditionReader.read("Não tem riscos, nem amolgadelas e nunca ferrugem").problemsPt()).isEmpty();
		assertThat(ConditionReader.read("Carro sem qualquer tipo de riscos ou amolgadelas").problemsPt()).isEmpty();
	}

	@Test
	void negation_stopsAtMasPoremContudo() {
		assertThat(ConditionReader.read("sem riscos mas tem ferrugem").problemsPt()).containsExactly("ferrugem");
		assertThat(ConditionReader.read("Sem riscos, porém tem ferrugem").problemsPt()).containsExactly("ferrugem");
		assertThat(ConditionReader.read("sem riscos, contudo tem ferrugem").problemsPt()).containsExactly("ferrugem");
	}

	@Test
	void negation_stopsAtNewVerb() {
		assertThat(ConditionReader.read("Sem acidentes, tem riscos").problemsPt()).containsExactly("riscos");
		assertThat(ConditionReader.read("Vendo por não precisar, tem ferrugem").problemsPt())
				.containsExactly("ferrugem");
		assertThat(ConditionReader.read("Nunca teve acidentes, tinha amolgadelas").problemsPt())
				.containsExactly("amolgadelas");
		assertThat(ConditionReader.read("Sem acidentes, apresenta ferrugem").problemsPt()).containsExactly("ferrugem");
		assertThat(ConditionReader.read("Sem acidentes, com riscos na porta").problemsPt()).containsExactly("riscos");
	}

	@Test
	void negation_directlyBeforeVerb_stillNegates() {
		assertThat(ConditionReader.read("Não tem riscos").problemsPt()).isEmpty();
		assertThat(ConditionReader.read("Nunca apresentou nem apresenta ferrugem").problemsPt()).isEmpty();
	}

	@Test
	void negation_butClauseAfterMas_counts() {
		assertThat(ConditionReader.read("Nunca deu qualquer problema mas tem ferrugem").problemsPt())
				.containsExactly("ferrugem");
	}

	@Test
	void negation_doesNotCrossSentenceEnd() {
		assertThat(ConditionReader.read("Sem acidentes. Riscos no para-choques.").problemsPt())
				.containsExactly("riscos");
	}

	@Test
	void negation_oneNegatedOneNot_counts() {
		assertThat(ConditionReader.read("Sem riscos no interior, mas a porta tem vários riscos").problemsPt())
				.containsExactly("riscos");
	}

	@Test
	void naoFumador_isNotANegation() {
		assertThat(ConditionReader.read("Dono não fumador, riscos no para-choques").problemsPt())
				.containsExactly("riscos");
	}

	@Test
	void negation_appliesToMaintenanceToo() {
		assertThat(ConditionReader.read("sem embraiagem nova").maintenancePt()).isEmpty();
	}

	@Test
	void wordBoundaries_noFalseHits() {
		assertThat(ConditionReader.read("Não fumador, pintura original, caixa automática").problemsPt()).isEmpty();
	}

	@Test
	void adjustment_capsAtThreeEach() {
		assertThat(new ConditionResult(List.of(), List.of()).rankAdjustment()).isZero();
		assertThat(new ConditionResult(List.of("a"), List.of()).rankAdjustment()).isCloseTo(-0.05, within(1e-9));
		assertThat(new ConditionResult(List.of(), List.of("a", "b")).rankAdjustment()).isCloseTo(0.06, within(1e-9));
		assertThat(new ConditionResult(List.of("a", "b", "c", "d", "e"), List.of("a", "b", "c", "d"))
				.rankAdjustment()).isCloseTo(0.09 - 0.15, within(1e-9));
	}
}
