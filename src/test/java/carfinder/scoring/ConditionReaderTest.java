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
		assertThat(ConditionReader.read(null)).isEqualTo(new ConditionResult(List.of(), List.of(), List.of(), false));
		assertThat(ConditionReader.read("  ")).isEqualTo(new ConditionResult(List.of(), List.of(), List.of(), false));
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
		assertThat(new ConditionResult(List.of(), List.of(), List.of(), false).rankAdjustment()).isZero();
		assertThat(new ConditionResult(List.of("a"), List.of(), List.of(), false).rankAdjustment()).isCloseTo(-0.05, within(1e-9));
		assertThat(new ConditionResult(List.of(), List.of("a", "b"), List.of(), false).rankAdjustment()).isCloseTo(0.06, within(1e-9));
		assertThat(new ConditionResult(List.of("a", "b", "c", "d", "e"), List.of("a", "b", "c", "d"), List.of(), false)
				.rankAdjustment()).isCloseTo(0.09 - 0.15, within(1e-9));
	}

	@Test
	void parts_inTitle() {
		assertThat(ConditionReader.read("Polo 1.4 para peças", null).parts()).isTrue();
	}

	@Test
	void parts_inDescription() {
		assertThat(ConditionReader.read("Polo 1.4", "Vende-se para peças, motor bom.").parts()).isTrue();
		assertThat(ConditionReader.read("Polo 1.4", "Vendo só para peças.").parts()).isTrue();
		assertThat(ConditionReader.read("Polo 1.4", "Vendo em bom estado.").parts()).isFalse();
	}

	@Test
	void parts_negated() {
		assertThat(ConditionReader.read("Polo 1.4", "Não é para peças, anda bem.").parts()).isFalse();
	}

	@Test
	void damage_eachLabelOnce() {
		assertThat(ConditionReader.read("Precisa de reparação").damagePt()).containsExactly("precisa de reparação");
		assertThat(ConditionReader.read("Precisa de arranjo").damagePt()).containsExactly("precisa de reparação");
		assertThat(ConditionReader.read("Carro para reparar").damagePt()).containsExactly("para reparar");
		assertThat(ConditionReader.read("Está avariada").damagePt()).containsExactly("avariado");
		assertThat(ConditionReader.read("Carro avariado").damagePt()).containsExactly("avariado");
		assertThat(ConditionReader.read("O carro não pega").damagePt()).containsExactly("não pega");
		assertThat(ConditionReader.read("O carro não anda").damagePt()).containsExactly("não anda");
		assertThat(ConditionReader.read("Motor gripado").damagePt()).containsExactly("motor gripado");
		assertThat(ConditionReader.read("Motor partido").damagePt()).containsExactly("motor gripado");
		assertThat(ConditionReader.read("Motor rebentado").damagePt()).containsExactly("motor gripado");
		assertThat(ConditionReader.read("Caixa partida").damagePt()).containsExactly("caixa partida");
		assertThat(ConditionReader.read("Caixa avariada").damagePt()).contains("caixa partida");
		assertThat(ConditionReader.read("Junta da cabeça queimada").damagePt()).containsExactly("junta da cabeça");
		assertThat(ConditionReader.read("Junta de cabeça").damagePt()).containsExactly("junta da cabeça");
		assertThat(ConditionReader.read("Carro batido").damagePt()).containsExactly("batido");
		assertThat(ConditionReader.read("Carro sinistrado").damagePt()).containsExactly("sinistrado");
		assertThat(ConditionReader.read("Carro acidentado").damagePt()).containsExactly("acidentado");
		assertThat(ConditionReader.read("Carro batido, batido na frente").damagePt()).containsExactly("batido");
	}

	@Test
	void damage_negated() {
		assertThat(ConditionReader.read("Nunca foi batido").damagePt()).isEmpty();
		assertThat(ConditionReader.read("Sem acidentes, não sinistrado").damagePt()).isEmpty();
		assertThat(ConditionReader.read("Não precisa de reparação").damagePt()).isEmpty();
	}

	@Test
	void naoPega_isDamage() {
		assertThat(ConditionReader.read("o carro não pega").damagePt()).containsExactly("não pega");
		// the negation of an earlier clause does not cancel it
		assertThat(ConditionReader.read("Sem riscos, não pega").damagePt()).containsExactly("não pega");
		assertThat(ConditionReader.read("Nunca teve problemas mas não anda").damagePt()).containsExactly("não anda");
	}

	@Test
	void titleDoesNotNegateDescription() {
		assertThat(ConditionReader.read("Sem riscos", "batido na frente").damagePt()).containsExactly("batido");
		assertThat(ConditionReader.read("Sem riscos", "riscos na porta").problemsPt()).containsExactly("riscos");
	}

	@Test
	void titleAndDescription_bothRead() {
		ConditionResult r = ConditionReader.read("Clio avariado", "Embraiagem nova.");
		assertThat(r.damagePt()).containsExactly("avariado");
		assertThat(r.maintenancePt()).containsExactly("embraiagem");
	}

	@Test
	void damageAndParts_doNotChangeRankAdjustment() {
		assertThat(new ConditionResult(List.of(), List.of(), List.of("batido"), true).rankAdjustment()).isZero();
		assertThat(ConditionReader.read(null, null)).isEqualTo(new ConditionResult(List.of(), List.of(), List.of(), false));
	}

	@Test
	void maintenance_marketplaceWordings() {
		ConditionResult r = ConditionReader.read("Kit de embreagem novo à cerca de de 2000kms ✅ Meti uns seletores novos "
				+ "(todos) ✅ Revisão feita à 500 kms, juntamente com 4 pneus novos da Michelin ✅");
		assertThat(r.maintenancePt()).containsExactlyInAnyOrder("embraiagem", "revisão", "pneus", "seletores");
		assertThat(ConditionReader.read("Precisa de pneus novos e de revisão.").maintenancePt()).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {"vendo citroen c3 às peças", "Peças VW Polo 6N 1.4", "vendo peças vw golf 4",
			"Vende-se peças de Clio"})
	void parts_otherWordings_inTitle(String title) {
		assertThat(ConditionReader.read(title, null).parts()).as(title).isTrue();
	}

	@Test
	void parts_descriptionThatIsJustPecas() {
		assertThat(ConditionReader.read("2002 Peugeot 206", "Peças\nMotor a gasolina").parts()).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {"Todas as peças originais.", "Peças novas: embraiagem e travões.",
			"Tem peças novas.", "Vendo carro com peças de origem.",
			"Bom estado. Peças trocadas recentemente: embraiagem e travões.", "Peças substituídas: correia.",
			"Peças mudadas há pouco tempo."})
	void parts_goodPecasWords_notParts(String description) {
		assertThat(ConditionReader.read("Polo 1.4", description).parts()).as(description).isFalse();
	}

	@Test
	void damage_motorABater() {
		assertThat(ConditionReader.read("ford fiesta 1994 motor abater restante top", null).damagePt())
				.containsExactly("motor a bater");
		assertThat(ConditionReader.read("Motor a bater.").damagePt()).containsExactly("motor a bater");
	}
}
