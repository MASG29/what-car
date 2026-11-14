package carfinder.source.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import carfinder.geo.Places;
import carfinder.knowledge.KnowledgeLoader;
import carfinder.listing.Fuel;
import carfinder.listing.RawListing;
import carfinder.listing.Site;
import carfinder.source.FeedResult;
import carfinder.source.SourceException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class MarketplaceFeedParserTest {

	static final MarketplaceFeedParser PARSER = new MarketplaceFeedParser(
			new MarketplaceTitle(KnowledgeLoader.loadClasspath()),
			new Places(Paths.get("src/test/resources/fixtures/geo/places-test.tsv")));

	static FeedResult fixture() throws IOException {
		return PARSER.parse(HarReaderTest.bodies(HarReaderTest.FIXTURE).stream());
	}

	static Map<String, RawListing> live() throws IOException {
		return fixture().live().stream().collect(Collectors.toMap(RawListing::externalId, Function.identity()));
	}

	@Test
	void parsesSixVehicles_skipsNonVehicle() throws IOException {
		FeedResult r = fixture();

		// 6 vehicles: 4 live (1001 appears twice, counted once), 2 sold/pending; the sofa is not a vehicle
		assertThat(r.live()).extracting(RawListing::externalId).containsExactlyInAnyOrder("1001", "1002", "1003",
				"1006");
		RawListing yaris = live().get("1001");
		assertThat(yaris.site()).isEqualTo(Site.MARKETPLACE);
		assertThat(yaris.url()).isEqualTo("https://www.facebook.com/marketplace/item/1001/");
		assertThat(yaris.title()).isEqualTo("2004 Toyota Yaris 1.3");
		assertThat(yaris.priceEur()).isEqualTo(1500);
		assertThat(yaris.make()).isEqualTo("Toyota");
		assertThat(yaris.model()).isEqualTo("Yaris");
		assertThat(yaris.year()).isEqualTo(2004);
		assertThat(yaris.km()).isEqualTo(146000);
		assertThat(yaris.city()).isEqualTo("Ponte de Lima");
		assertThat(yaris.district()).isEqualTo("Viana do Castelo");
		assertThat(yaris.photoCount()).isZero();
		assertThat(yaris.cc()).isNull();
		assertThat(yaris.powerCv()).isNull();
		assertThat(yaris.gearbox()).isNull();
		RawListing polo = live().get("1002");
		assertThat(polo.make()).isEqualTo("Volkswagen");
		assertThat(polo.fuel()).isEqualTo(Fuel.DIESEL);
		assertThat(polo.km()).isEqualTo(159325);
		assertThat(live().get("1006").km()).isEqualTo(1500);
		assertThat(live().get("1006").fuel()).isEqualTo(Fuel.PETROL);
	}

	@Test
	void soldAndPendingAreUnavailable() throws IOException {
		assertThat(fixture().unavailableIds()).containsExactlyInAnyOrder("1004", "1005");
	}

	@Test
	void firstBatchFromDocumentIsRead() throws IOException {
		String doc = HarReaderTest.bodies(HarReaderTest.FIXTURE).getFirst();

		assertThat(PARSER.parse(Stream.of(doc)).live()).extracting(RawListing::externalId)
				.containsExactlyInAnyOrder("1001", "1002");
	}

	@Test
	void sellerTitleWinsOverShortCustomTitle() throws IOException {
		// real feeds: custom_title is Facebook's short "year make model"; the seller's title keeps engine and condition
		assertThat(live().get("1003").title()).isEqualTo("Renault Clio 1.2 bom estado");
		assertThat(live().get("1003").make()).isEqualTo("Renault");
		assertThat(live().get("1003").km()).isNull(); // empty subtitle
	}

	private static RawListing one(String sellerTitle, String customTitle) {
		String item = """
				{"data":{"x":[{"id":"7","marketplace_listing_title":"%s","custom_title":"%s",
				"marketplace_listing_category_id":"807311116002614","listing_price":{"amount":"1500.00"}}]}}"""
				.formatted(sellerTitle, customTitle).replace("\n", "");
		return PARSER.parse(Stream.of(item)).live().getFirst();
	}

	@Test
	void customTitleFillsWhatTheSellerTitleLacks() {
		// custom_title holds the year/make/model the seller picked in Facebook's form
		RawListing noYear = one("Toyota Yaris 1.3", "2004 Toyota Yaris");
		assertThat(noYear.title()).isEqualTo("Toyota Yaris 1.3");
		assertThat(noYear.year()).isEqualTo(2004);

		RawListing vague = one("carro economico", "2004 Toyota Yaris");
		assertThat(vague.make()).isEqualTo("Toyota");
		assertThat(vague.model()).isEqualTo("Yaris");
		assertThat(vague.year()).isEqualTo(2004);

		// the two disagree on the car: the seller's words win and the other car's year is not borrowed
		RawListing conflict = one("Toyota Yaris 1.3", "2007 Renault Clio");
		assertThat(conflict.make()).isEqualTo("Toyota");
		assertThat(conflict.year()).isNull();
	}

	@Test
	void placeholderPrice_keptAsOneEuro() throws IOException {
		// "1.00" stays 1 €, so the existing placeholder rule excludes it with a reason (see SearchServiceTest)
		assertThat(live().get("1003").priceEur()).isEqualTo(1);
	}

	@Test
	void noMarketplaceBodies_throwsFilterMessage() {
		assertThatThrownBy(() -> PARSER.parse(Stream.empty())).isInstanceOf(SourceException.class)
				.hasMessage("Este ficheiro não tem anúncios do Marketplace. Confirma os filtros Doc e Fetch/XHR.");
	}

	@Test
	void bodiesWithoutItems_throwsFormatChanged() {
		assertThatThrownBy(() -> PARSER.parse(Stream.of("for (;;);{\"data\":{\"viewer\":{}}}", "<html></html>")))
				.isInstanceOf(SourceException.class).hasMessage(Site.MARKETPLACE.formatChangedPt());
	}

	@Test
	void districtFromThreePartDisplayName() throws IOException {
		assertThat(live().get("1002").district()).isEqualTo("Aveiro");
		assertThat(live().get("1002").city()).isEqualTo("Paramos");
	}

	@Test
	void middlePartNotADistrict_isNull() throws IOException {
		assertThat(live().get("1003").district()).isNull();
		assertThat(live().get("1006").district()).isNull(); // "Braga, Portugal": two parts
	}
}
