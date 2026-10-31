package carfinder.buying;

import static carfinder.buying.RealCostCalculatorTest.ad;
import static carfinder.buying.RealCostCalculatorTest.match;
import static carfinder.buying.RealCostCalculatorTest.noEngine;
import static org.assertj.core.api.Assertions.assertThat;

import carfinder.knowledge.Timing;
import carfinder.listing.Fuel;
import carfinder.listing.RawListing;
import carfinder.listing.Site;
import carfinder.listing.TestListings;
import org.junit.jupiter.api.Test;

class ChecklistBuilderTest {

	private final ChecklistBuilder b = new ChecklistBuilder();

	@Test
	void checklist_alwaysHasThreeLinks() {
		Checklist c = b.build(ad(2005, 3, null), noEngine());
		assertThat(c.links()).extracting(Checklist.Link::url).containsExactly("https://recall.motordata.pt",
				"https://www.gov.pt/servicos/obter-relatorio-de-resultados-das-inspecoes-tecnicas-de-veiculo",
				"https://www.registoautomovel.pt/blog/certidao-permanente-automovel");
	}

	@Test
	void checklist_alwaysAsksKmAndPlate() {
		assertThat(b.build(ad(2005, 3, null), match(Timing.CHAIN)).questionsPt()).contains("Quantos km tem?",
				"Qual é a matrícula?");
	}

	@Test
	void checklist_chainEngine_asksColdStart() {
		var q = b.build(ad(2005, 3, null), match(Timing.CHAIN)).questionsPt();
		assertThat(q).contains("Posso ouvir o motor a trabalhar a frio?")
				.doesNotContain("Quando foi trocada a correia de distribuição? Há fatura?");
	}

	@Test
	void checklist_beltOrUnknown_asksBeltInvoice() {
		for (var m : new carfinder.matching.CarMatch[] { match(Timing.BELT), match(Timing.UNKNOWN), noEngine() }) {
			var q = b.build(ad(2005, 3, null), m).questionsPt();
			assertThat(q).contains("Quando foi trocada a correia de distribuição? Há fatura?")
					.doesNotContain("Posso ouvir o motor a trabalhar a frio?");
		}
	}

	@Test
	void checklist_importedNull_asksOrigin_elseNot() {
		assertThat(b.build(ad(2005, 3, null), noEngine()).questionsPt())
				.contains("O carro é nacional ou importado?");
		var known = TestListings.of(new RawListing(Site.STANDVIRTUAL, "t", "https://example.test/1", "t", 1500,
				"Toyota", "Yaris", null, 2005, 3, 1, Fuel.PETROL, 998, 68, null, 5, null, null, null, 0, null,
				Boolean.TRUE, false), true);
		assertThat(b.build(known, noEngine()).questionsPt()).doesNotContain("O carro é nacional ou importado?");
	}
}
