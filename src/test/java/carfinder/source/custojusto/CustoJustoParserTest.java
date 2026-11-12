package carfinder.source.custojusto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import carfinder.geo.Places;
import carfinder.knowledge.KnowledgeLoader;
import carfinder.listing.Fuel;
import carfinder.listing.RawListing;
import carfinder.listing.SellerType;
import carfinder.listing.Site;
import carfinder.source.SearchPage;
import carfinder.source.SourceException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class CustoJustoParserTest {

	static final CustoJustoParser PARSER = new CustoJustoParser(KnowledgeLoader.loadClasspath(),
			new Places(Paths.get("src/test/resources/fixtures/geo/places-test.tsv")));

	static String fixture(String name) throws IOException {
		try (InputStream in = CustoJustoParserTest.class.getResourceAsStream("/fixtures/" + name)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	void parsesTotalAndThreeListings() throws IOException {
		SearchPage page = PARSER.parseSearchPage(fixture("custojusto-search.html"));

		assertThat(page.totalCount()).isEqualTo(120);
		assertThat(page.pageSize()).isEqualTo(40);
		// the gallery ad and the "buy" ad are not listings
		assertThat(page.listings()).extracting(RawListing::externalId).containsExactly("90000001", "90000002",
				"90000003");
	}

	@Test
	void mapsFieldsOfFirstItem() throws IOException {
		RawListing r = PARSER.parseSearchPage(fixture("custojusto-search.html")).listings().getFirst();

		assertThat(r.site()).isEqualTo(Site.CUSTOJUSTO);
		assertThat(r.url()).isEqualTo(
				"https://www.custojusto.pt/braga/veiculos/carros-usados/citadino/renault-clio-1-2-16v-90000001");
		assertThat(r.title()).isEqualTo("Renault Clio 1.2 16V");
		assertThat(r.priceEur()).isEqualTo(1290);
		assertThat(r.make()).isEqualTo("Renault");
		assertThat(r.model()).isEqualTo("Clio");
		assertThat(r.year()).isEqualTo(2005);
		assertThat(r.fuel()).isEqualTo(Fuel.PETROL);
		assertThat(r.gearbox()).isEqualTo("manual");
		assertThat(r.sellerType()).isEqualTo(SellerType.PRIVATE);
		assertThat(r.city()).isEqualTo("Braga");
		assertThat(r.district()).isEqualTo("Braga");
		assertThat(r.photoCount()).isEqualTo(6);
		// search items never carry km on this site: the advert page gives them
		assertThat(r.km()).isNull();
	}

	@Test
	void mapsDealerDieselAutomatic() throws IOException {
		RawListing r = PARSER.parseSearchPage(fixture("custojusto-search.html")).listings().get(1);

		assertThat(r.sellerType()).isEqualTo(SellerType.DEALER);
		assertThat(r.fuel()).isEqualTo(Fuel.DIESEL);
		assertThat(r.gearbox()).isEqualTo("automatic");
		assertThat(r.make()).isEqualTo("Peugeot");
		assertThat(r.model()).isEqualTo("307");
		assertThat(r.district()).isEqualTo("Porto");
	}

	@Test
	void missingFields_areNull() throws IOException {
		RawListing r = PARSER.parseSearchPage(fixture("custojusto-search.html")).listings().get(2);

		assertThat(r.title()).isEqualTo("Carro antigo");
		assertThat(r.make()).isNull();
		assertThat(r.model()).isNull();
		assertThat(r.year()).isNull();
		assertThat(r.fuel()).isNull();
		assertThat(r.gearbox()).isNull();
		assertThat(r.km()).isNull();
	}

	@Test
	void missingStructure_throwsFormatChanged() throws IOException {
		assertThatThrownBy(() -> PARSER.parseSearchPage("<html><body>Olá</body></html>"))
				.isInstanceOf(SourceException.class).hasMessage("CustoJusto mudou o formato da página");
		String noItems = fixture("custojusto-search.html").replace("\"listItems\"", "\"otherItems\"");
		assertThatThrownBy(() -> PARSER.parseSearchPage(noItems))
				.isInstanceOf(SourceException.class).hasMessage("CustoJusto mudou o formato da página");
	}
}
