package carfinder.source.standvirtual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import carfinder.listing.Fuel;
import carfinder.listing.RawListing;
import carfinder.listing.SellerType;
import carfinder.listing.Site;
import carfinder.source.SourceException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class StandvirtualAdvertParserTest {

	private static final String URL =
			"https://www.standvirtual.com/carros/anuncio/renault-clio-ver-1-5-dci-dynamique-IDfake0002.html";

	private final StandvirtualAdvertParser parser = new StandvirtualAdvertParser();

	private static String fixture() throws IOException {
		try (InputStream in = StandvirtualAdvertParserTest.class.getResourceAsStream("/fixtures/standvirtual-advert.html")) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	/** A page whose advert object is the given JSON. */
	private static String page(String advertJson) {
		return "<script id=\"__NEXT_DATA__\" type=\"application/json\">{\"props\":{\"pageProps\":{\"advert\":"
				+ advertJson + "}}}</script>";
	}

	@Test
	void readsDoorsMonthPhotosDescription() throws IOException {
		RawListing r = parser.parse(fixture(), URL);

		assertThat(r).isEqualTo(new RawListing(Site.STANDVIRTUAL, "1000000002", URL,
				"Renault Clio 1.5 dCi Dynamique", 4500, "Renault", "Clio", "1.5 dCi Dynamique", 2014, 6, 185000,
				Fuel.DIESEL, 1461, 90, "manual", 5, SellerType.DEALER, "Porto", "Gaia", 12,
				"Carro de teste.\nRevisões em dia.", false, false));
	}

	@Test
	void isPartsTrue_setsParts() {
		RawListing r = parser.parse(page("""
				{"id":"7","title":"Peças Fiat Punto","isParts":true,"parametersDict":{}}"""), "https://example.test/7");

		assertThat(r.parts()).isTrue();
		assertThat(r.url()).isEqualTo("https://example.test/7");
		assertThat(r.make()).isNull();
		assertThat(r.doors()).isNull();
		assertThat(r.month()).isNull();
		assertThat(r.imported()).isNull();
		assertThat(r.sellerType()).isNull();
		assertThat(r.description()).isNull();
		assertThat(r.photoCount()).isZero();
	}

	@Test
	void importedFlag_mapped() throws IOException {
		assertThat(parser.parse(fixture(), URL).imported()).isFalse();

		RawListing r = parser.parse(page("""
				{"id":"8","title":"BMW 320d","parametersDict":{"is_imported_car":{"values":[{"value":"1","label":"Sim"}]}}}"""),
				"https://example.test/8");

		assertThat(r.imported()).isTrue();
	}

	@Test
	void longDescription_truncatedTo4000() {
		RawListing r = parser.parse(page("{\"id\":\"9\",\"title\":\"Seat Ibiza\",\"parametersDict\":{},\"description\":\""
				+ "a".repeat(5000) + "\"}"), "https://example.test/9");

		assertThat(r.description()).hasSize(4000);
	}

	@Test
	void missingAdvert_throws() {
		assertThatThrownBy(() -> parser.parse(
				"<script id=\"__NEXT_DATA__\" type=\"application/json\">{\"props\":{\"pageProps\":{}}}</script>", URL))
				.isInstanceOf(SourceException.class)
				.hasMessage(StandvirtualParser.FORMAT_CHANGED);
		assertThatThrownBy(() -> parser.parse("<html>sem dados</html>", URL))
				.isInstanceOf(SourceException.class)
				.hasMessage(StandvirtualParser.FORMAT_CHANGED);
	}
}
