package carfinder.source.custojusto;

import static carfinder.source.custojusto.CustoJustoParserTest.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import carfinder.geo.Places;
import carfinder.knowledge.KnowledgeLoader;
import carfinder.listing.Fuel;
import carfinder.listing.RawListing;
import carfinder.listing.SellerType;
import carfinder.listing.Site;
import carfinder.scoring.ConditionReader;
import carfinder.source.SourceException;
import java.io.IOException;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class CustoJustoAdvertParserTest {

	static final String URL = "https://www.custojusto.pt/braga/veiculos/carros-usados/citadino/renault-clio-1-2-16v-90000001";
	static final CustoJustoAdvertParser PARSER = new CustoJustoAdvertParser(KnowledgeLoader.loadClasspath(),
			new Places(Paths.get("src/test/resources/fixtures/geo/places-test.tsv")));

	private static String withBody(String body) throws IOException {
		return fixture("custojusto-advert.html").replaceFirst("\"body\":\"[^\"]*\"", "\"body\":\"" + body + "\"");
	}

	@Test
	void advert_parsesDescriptionAndPhotos() throws IOException {
		RawListing r = PARSER.parse(fixture("custojusto-advert.html"), URL);

		assertThat(r.site()).isEqualTo(Site.CUSTOJUSTO);
		assertThat(r.externalId()).isEqualTo("90000001");
		assertThat(r.url()).isEqualTo(URL);
		assertThat(r.title()).isEqualTo("Renault Clio 1.2 16V");
		assertThat(r.priceEur()).isEqualTo(1290);
		assertThat(r.make()).isEqualTo("Renault");
		assertThat(r.model()).isEqualTo("Clio");
		assertThat(r.year()).isEqualTo(2005);
		assertThat(r.month()).isEqualTo(3);
		assertThat(r.km()).isEqualTo(216000);
		assertThat(r.fuel()).isEqualTo(Fuel.PETROL);
		assertThat(r.gearbox()).isEqualTo("manual");
		assertThat(r.cc()).isEqualTo(1149);
		assertThat(r.powerCv()).isEqualTo(75);
		assertThat(r.sellerType()).isEqualTo(SellerType.PRIVATE);
		assertThat(r.city()).isEqualTo("Braga");
		assertThat(r.district()).isEqualTo("Braga");
		assertThat(r.photoCount()).isEqualTo(3); // one of four images is deleted
		assertThat(r.description()).isEqualTo(
				"Vendo Renault Clio 1.2 16V, sempre em garagem.\nQuilómetros: 216.000 km\nNão é para peças, está tudo a funcionar.");
		assertThat(r.parts()).isFalse();
		assertThat(ConditionReader.read(r.title(), r.description()).parts()).isFalse();
	}

	@Test
	void missingKm_isNull() throws IOException {
		RawListing r = PARSER.parse(withBody("Vendo carro em bom estado."), URL);

		assertThat(r.km()).isNull();
	}

	@Test
	void kmOutsideTheSitesBand_isNull() throws IOException {
		// "revisão aos 500 km" is not the odometer: the site's own band says 200 000 - 249 999
		RawListing r = PARSER.parse(withBody("Revisão feita aos 5000 km de distância."), URL);

		assertThat(r.km()).isNull();
	}

	@Test
	void siteLabel_resolvesToKnowledgeBaseNames() throws IOException {
		String html = fixture("custojusto-advert.html").replace("\"name\":\"Renault\"", "\"name\":\"VW\"")
				.replace("\"name\":\"Clio\"", "\"name\":\"Golf\"").replace("\"title\":\"Renault Clio 1.2 16V\"",
						"\"title\":\"Carro de família\"");

		RawListing r = PARSER.parse(html, URL);

		assertThat(r.make()).isEqualTo("Volkswagen");
		assertThat(r.model()).isEqualTo("Golf");
	}

	@Test
	void longDescription_isTruncated() throws IOException {
		RawListing r = PARSER.parse(withBody("a".repeat(5000)), URL);

		assertThat(r.description()).hasSize(4000);
	}

	@Test
	void missingStructure_throwsFormatChanged() {
		assertThatThrownBy(() -> PARSER.parse("<html></html>", URL)).isInstanceOf(SourceException.class)
				.hasMessage("CustoJusto mudou o formato da página");
	}
}
