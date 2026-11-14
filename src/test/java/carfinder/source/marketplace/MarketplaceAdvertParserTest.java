package carfinder.source.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import carfinder.geo.Places;
import carfinder.knowledge.KnowledgeLoader;
import carfinder.listing.Fuel;
import carfinder.listing.RawListing;
import carfinder.listing.Site;
import carfinder.source.LoginWallException;
import carfinder.source.SoldOrReservedException;
import carfinder.source.SourceException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class MarketplaceAdvertParserTest {

	static final String URL = "https://www.facebook.com/marketplace/item/9000000000000001/";
	static final MarketplaceAdvertParser PARSER = new MarketplaceAdvertParser(
			new MarketplaceTitle(KnowledgeLoader.loadClasspath()),
			new Places(Paths.get("src/test/resources/fixtures/geo/places-test.tsv")));

	static String fixture() throws IOException {
		try (InputStream in = MarketplaceAdvertParserTest.class.getResourceAsStream("/fixtures/marketplace-advert.html")) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	/** The fixture with another description (JSON-escaped). */
	static String withDescription(String text) throws IOException {
		String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
		return fixture().replaceFirst("\"redacted_description\":\\{\"text\":\"[^\"]*\"\\}",
				java.util.regex.Matcher.quoteReplacement("\"redacted_description\":{\"text\":\"" + escaped + "\"}"));
	}

	@Test
	void parsesDescriptionAndAttributes() throws IOException {
		RawListing r = PARSER.parse(fixture(), URL);

		assertThat(r.site()).isEqualTo(Site.MARKETPLACE);
		assertThat(r.externalId()).isEqualTo("9000000000000001");
		assertThat(r.url()).isEqualTo(URL);
		assertThat(r.title()).isEqualTo("Renault Clio");
		assertThat(r.priceEur()).isEqualTo(1500);
		assertThat(r.make()).isEqualTo("Renault");
		assertThat(r.model()).isEqualTo("Clio");
		assertThat(r.description()).startsWith("Vendo Renault Clio 1.5 dCi de 2007.\nGasóleo, caixa manual.");
		assertThat(r.year()).isEqualTo(2007);
		assertThat(r.km()).isEqualTo(182000);
		assertThat(r.fuel()).isEqualTo(Fuel.DIESEL);
		assertThat(r.gearbox()).isEqualTo("manual");
		assertThat(r.photoCount()).isEqualTo(3);
		assertThat(r.city()).isEqualTo("Barcelos");
		assertThat(r.district()).isEqualTo("Braga");
		assertThat(r.parts()).isFalse();
	}

	@Test
	void sellerDataNeverRead() throws IOException {
		String all = PARSER.parse(fixture(), URL).toString();

		assertThat(all).doesNotContain("Fictício", "5550000000000001", "900 000 000");
	}

	@Test
	void readsTheRequestedItem_notSimilarListings() throws IOException {
		RawListing r = PARSER.parse(fixture(), URL);

		assertThat(r.description()).doesNotContain("1999");
		assertThat(r.year()).isEqualTo(2007);
	}

	@Test
	void descriptionTruncatedTo4000() throws IOException {
		RawListing r = PARSER.parse(withDescription("a".repeat(5000)), URL);

		assertThat(r.description()).hasSize(4000);
	}

	@Test
	void titleYearAndFuelWinOverDescription() throws IOException {
		String html = withDescription("Gasolina, de 2003.").replace("\"marketplace_listing_title\":\"Renault Clio\"",
				"\"marketplace_listing_title\":\"2005 Renault Clio 1.5 dCi\"");

		RawListing r = PARSER.parse(html, URL);

		assertThat(r.year()).isEqualTo(2005);
		assertThat(r.fuel()).isEqualTo(Fuel.DIESEL);
	}

	@Test
	void conflictingDescriptionValues_leftNull() throws IOException {
		RawListing r = PARSER.parse(withDescription(
				"Ano 2004, primeiro registo 2006. Revisão aos 140 000 km, tem 180.000 km. Gasolina ou gasóleo? "
						+ "Caixa manual, também tenho caixa automática."), URL);

		assertThat(r.year()).isNull();
		assertThat(r.km()).isNull();
		assertThat(r.fuel()).isNull();
		assertThat(r.gearbox()).isNull();
	}

	@Test
	void descriptionShapes() throws IOException {
		RawListing a = PARSER.parse(withDescription("Ano: 2009. 150 mil km. Caixa automática. GPL."), URL);
		assertThat(a.year()).isEqualTo(2009);
		assertThat(a.km()).isEqualTo(150000);
		assertThat(a.gearbox()).isEqualTo("automatic");
		assertThat(a.fuel()).isEqualTo(Fuel.LPG);

		RawListing b = PARSER.parse(withDescription("Matrícula 05/2011, 98000kms, consumo 5 l/100 km, 7 lugares"),
				URL);
		assertThat(b.year()).isEqualTo(2011);
		assertThat(b.km()).isEqualTo(98000);
		assertThat(b.fuel()).isNull();
		assertThat(b.gearbox()).isNull();
	}

	@Test
	void noAdData_throwsLoginWall() {
		String html = "<html><body><script type=\"application/json\" data-sjs>{\"require\":[]}</script></body></html>";

		assertThatThrownBy(() -> PARSER.parse(html, URL)).isInstanceOf(LoginWallException.class)
				.isInstanceOf(SourceException.class)
				.hasMessage("O Facebook não mostra este anúncio sem login; fica com os dados importados");
	}

	@Test
	void soldItem_throwsSoNoRevival() throws IOException {
		String html = fixture().replace("\"is_sold\":false", "\"is_sold\":true");

		assertThatThrownBy(() -> PARSER.parse(html, URL)).isInstanceOf(SoldOrReservedException.class)
				.isNotInstanceOf(LoginWallException.class);
	}

	@Test
	void plusForSpaces_descriptionDecoded() throws IOException {
		RawListing r = PARSER.parse(withDescription("Levou+essa+manutencao,+caixa+manual.\nGasolina+de+2004."), URL);

		assertThat(r.description()).isEqualTo("Levou essa manutencao, caixa manual.\nGasolina de 2004.");
		assertThat(r.gearbox()).isEqualTo("manual");
		assertThat(r.year()).isEqualTo(2004);
	}

	@Test
	void plusInsideNormalText_kept() throws IOException {
		assertThat(PARSER.parse(withDescription("Rádio + GPS, ar condicionado."), URL).description())
				.isEqualTo("Rádio + GPS, ar condicionado.");
	}

	@Test
	void futureDates_notTheCarsYear() throws IOException {
		assertThat(PARSER.parse(withDescription("Inspeção válida até junho de 2027. Carro de 2001."), URL).year())
				.isEqualTo(2001);
		assertThat(PARSER.parse(withDescription("Inspeção válida até março de 2025."), URL).year()).isNull();
		assertThat(PARSER.parse(withDescription("Primeiro registo: julho de 1998."), URL).year()).isEqualTo(1998);
	}
}
