package carfinder.source.olx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import carfinder.geo.Places;
import carfinder.knowledge.KnowledgeLoader;
import carfinder.listing.Fuel;
import carfinder.listing.RawListing;
import carfinder.listing.SellerType;
import carfinder.listing.Site;
import carfinder.source.SourceException;
import carfinder.source.marketplace.HarReader;
import carfinder.source.marketplace.MarketplaceFeedParser.FeedResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class OlxFeedParserTest {

	static final Path FIXTURE = Path.of("src/test/resources/fixtures/olx-feed.har");
	/** Seller and car-identity values planted in the fixture; none may come out of the parser. */
	public static final List<String> SENTINELS = List.of("Vendedor Olx Teste", "77777001", "Contacto Sentinela",
			"lojasentinela", "41.123456", "AA-00-ZZ", "VINSENTINEL0001", "uuid-sentinel", "sentinel.jpg");

	static final OlxFeedParser PARSER = new OlxFeedParser(KnowledgeLoader.loadClasspath(),
			new Places(Paths.get("src/test/resources/fixtures/geo/places-test.tsv")));

	static FeedResult parseFixture() throws IOException {
		return parse(Files.readString(FIXTURE));
	}

	static FeedResult parse(String har) throws IOException {
		try (InputStream in = new java.io.ByteArrayInputStream(har.getBytes(java.nio.charset.StandardCharsets.UTF_8));
				Stream<String> bodies = HarReader.bodies(in, OlxFeedParser.HAR)) {
			return PARSER.parse(bodies);
		}
	}

	static RawListing byId(FeedResult f, String id) {
		return f.live().stream().filter(r -> r.externalId().equals(id)).findFirst().orElseThrow();
	}

	@Test
	void parsesBothShapes_dedupesById() throws IOException {
		FeedResult f = parseFixture();

		// 900001 is in the page and in graphql; 900004 is not active; 900002/900005 are Standvirtual mirrors
		assertThat(f.live()).extracting(RawListing::externalId).containsExactlyInAnyOrder("900001", "900003");
		assertThat(f.live()).allMatch(r -> r.site() == Site.OLX);
	}

	@Test
	void standvirtualMirror_skippedAndCounted() throws IOException {
		FeedResult f = parseFixture();

		assertThat(f.skipped()).isEqualTo(2);
		assertThat(f.unavailableIds()).doesNotContain("900002", "900005");
	}

	@Test
	void mapsFieldsOfPageItem() throws IOException {
		// page document only: the graphql entry renamed so the filter skips it
		RawListing r = byId(parse(Files.readString(FIXTURE).replace("/apigateway/graphql", "/x/graphql")), "900001");

		assertClio(r);
	}

	@Test
	void mapsFieldsOfGraphqlItem() throws IOException {
		// graphql only: the document entry renamed so the filter skips it
		RawListing r = byId(parse(Files.readString(FIXTURE).replace("/carros-motos-e-barcos/carros/braga/", "/x/")),
				"900001");

		assertClio(r);
	}

	private static void assertClio(RawListing r) {
		assertThat(r.site()).isEqualTo(Site.OLX);
		assertThat(r.url()).isEqualTo("https://www.olx.pt/d/anuncio/renault-clio-1.2-16v-gasolina-IDX900001.html");
		assertThat(r.title()).isEqualTo("Renault Clio 1.2 16V gasolina");
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
		assertThat(r.doors()).isEqualTo(5);
		assertThat(r.sellerType()).isEqualTo(SellerType.PRIVATE);
		assertThat(r.city()).isEqualTo("Braga");
		assertThat(r.district()).isEqualTo("Braga");
		assertThat(r.photoCount()).isEqualTo(6);
		assertThat(r.description()).isEqualTo("Bom estado.\nNão é para peças.");
		assertThat(r.parts()).isFalse();
	}

	@Test
	void makeFromCategoryWhenTitleHasNone() throws IOException {
		RawListing r = byId(parseFixture(), "900003");

		assertThat(r.make()).isEqualTo("Toyota");
		assertThat(r.model()).isEqualTo("Yaris");
		assertThat(r.sellerType()).isEqualTo(SellerType.DEALER);
		assertThat(r.fuel()).isEqualTo(Fuel.DIESEL);
		assertThat(r.gearbox()).isEqualTo("automatic");
		assertThat(r.doors()).isEqualTo(3);
	}

	@Test
	void longDescription_cutTo4000() throws IOException {
		FeedResult f = parse(Files.readString(FIXTURE).replace("Carro com 180.000 km reais.", "x".repeat(4001)));

		assertThat(byId(f, "900003").description()).hasSize(4000);
	}

	@Test
	void kmFromDescriptionWhenParamMissing() throws IOException {
		assertThat(byId(parseFixture(), "900003").km()).isEqualTo(180000);
	}

	@Test
	void notActive_isUnavailable() throws IOException {
		assertThat(parseFixture().unavailableIds()).containsExactly("900004");
	}

	@Test
	void partsInDescription_flagged() throws IOException {
		FeedResult f = parse(Files.readString(FIXTURE).replace("Não é para peças.", "Vendo para peças."));

		assertThat(byId(f, "900001").parts()).isTrue();
	}

	@Test
	void noListings_throwsNoAds() {
		assertThatThrownBy(() -> parse("{\"log\":{\"entries\":[]}}")).isInstanceOf(SourceException.class)
				.hasMessage(OlxFeedParser.NO_ADS);
		// an OLX graphql body that is some other query
		String other = "{\"log\":{\"entries\":[{\"request\":{\"method\":\"POST\",\"url\":\"https://www.olx.pt/apigateway/graphql\"},"
				+ "\"response\":{\"content\":{\"mimeType\":\"application/json\",\"text\":\"{\\\"data\\\":{\\\"myAds\\\":1}}\"}}}]}}";
		assertThatThrownBy(() -> parse(other)).isInstanceOf(SourceException.class).hasMessage(OlxFeedParser.NO_ADS);
	}

	@Test
	void itemWithoutId_throwsFormatChanged() {
		assertThatThrownBy(() -> parse(Files.readString(FIXTURE).replace("\\\"id\\\": 900003", "\\\"nid\\\": 900003")))
				.isInstanceOf(SourceException.class).hasMessage(Site.OLX.formatChangedPt());
	}

	@Test
	void sentinelsNeverRead() throws IOException {
		FeedResult f = parseFixture();

		assertThat(f.live().toString()).doesNotContain(SENTINELS);
		assertThat(f.unavailableIds().toString()).doesNotContain(SENTINELS);
	}
}
