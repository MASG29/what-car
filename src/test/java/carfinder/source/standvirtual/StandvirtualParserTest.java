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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class StandvirtualParserTest {

	private static final String PT_MESSAGE = "Standvirtual mudou o formato da página";

	private final StandvirtualParser parser = new StandvirtualParser();

	private static String fixture() throws IOException {
		try (InputStream in = StandvirtualParserTest.class.getResourceAsStream("/fixtures/standvirtual-search.html")) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	void parsesTotalCountAndThreeListings() throws IOException {
		SearchPage page = parser.parseSearchPage(fixture());

		assertThat(page.totalCount()).isEqualTo(629);
		assertThat(page.pageSize()).isEqualTo(32);
		assertThat(page.listings()).hasSize(3);
	}

	@Test
	void mapsParametersToFields() throws IOException {
		RawListing first = parser.parseSearchPage(fixture()).listings().getFirst();

		assertThat(first).isEqualTo(new RawListing(Site.STANDVIRTUAL, "1000000001",
				"https://www.standvirtual.com/carros/anuncio/toyota-yaris-ver-1-3-vvt-i-sol-IDfake0001.html",
				"Toyota Yaris 1.3 VVT-i Sol", 1290, "Toyota", "Yaris", "1.3 VVT-i Sol", 2005, null, 216000,
				Fuel.PETROL, 1299, 87, "manual", null, SellerType.PRIVATE, "Braga", "Guimarães", 0, null, null,
				false));

		RawListing second = parser.parseSearchPage(fixture()).listings().get(1);
		assertThat(second.fuel()).isEqualTo(Fuel.DIESEL);
		assertThat(second.sellerType()).isEqualTo(SellerType.DEALER);
		assertThat(second.district()).isEqualTo("Porto");
	}

	@Test
	void missingMileage_isNull() throws IOException {
		RawListing third = parser.parseSearchPage(fixture()).listings().get(2);

		assertThat(third.km()).isNull();
		assertThat(third.cc()).isNull();
		assertThat(third.version()).isNull();
		assertThat(third.year()).isEqualTo(2003);
	}

	@Test
	void missingNextData_throwsSourceExceptionWithPtMessage() {
		assertThatThrownBy(() -> parser.parseSearchPage("<html><body>Olá</body></html>"))
				.isInstanceOf(SourceException.class)
				.hasMessage(PT_MESSAGE);
	}

	@Test
	void advertSearchWithoutEdges_throwsInsteadOfEmptySuccess() throws IOException {
		String html = fixture().replace("\\\"edges\\\"", "\\\"items\\\"");

		assertThatThrownBy(() -> parser.parseSearchPage(html))
				.isInstanceOf(SourceException.class)
				.hasMessage(PT_MESSAGE);
	}

	@Test
	void searchUrl_containsPriceAndPage() {
		URI uri = StandvirtualUrls.search(2500, 3);

		assertThat(uri.getHost()).isEqualTo("www.standvirtual.com");
		assertThat(uri.getPath()).isEqualTo("/carros");
		assertThat(uri.getQuery())
				.contains("search[filter_float_price:to]=2500")
				.contains("search[order]=created_at_first:desc")
				.contains("page=3");
	}
}
