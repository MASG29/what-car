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
	void checklist_kmKnown_asksToConfirmIt_alwaysAsksPlate() {
		// ad() has 150000 km; PT thousands separator is a no-break space
		assertThat(b.build(ad(2005, 3, null), match(Timing.CHAIN)).questionsPt())
				.contains("Confirma os 150\u00A0000 km no histórico das inspeções", "Qual é a matrícula?")
				.doesNotContain("Quantos km tem?");
	}

	@Test
	void checklist_kmBelowTenThousand_noSeparator() {
		var low = TestListings.of(new RawListing(Site.STANDVIRTUAL, "t", "https://example.test/1", "t", 1500,
				"Toyota", "Yaris", null, 2005, 3, 9500, Fuel.PETROL, 998, 68, null, 5, null, null, null, 0, null,
				null, false), true);
		assertThat(b.build(low, noEngine()).questionsPt()).contains("Confirma os 9500 km no histórico das inspeções");
	}

	@Test
	void checklist_kmUnknown_asksKm() {
		var noKm = TestListings.of(new RawListing(Site.STANDVIRTUAL, "t", "https://example.test/1", "t", 1500,
				"Toyota", "Yaris", null, 2005, 3, null, Fuel.PETROL, 998, 68, null, 5, null, null, null, 0, null,
				null, false), true);
		var q = b.build(noKm, noEngine()).questionsPt();
		assertThat(q).contains("Quantos km tem?", "Qual é a matrícula?");
		assertThat(q).noneMatch(x -> x.startsWith("Confirma os"));
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
