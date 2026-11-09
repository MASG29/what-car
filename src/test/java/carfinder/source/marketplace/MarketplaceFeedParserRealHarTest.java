package carfinder.source.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import carfinder.geo.Places;
import carfinder.knowledge.KnowledgeLoader;
import carfinder.listing.RawListing;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Local check against a real HAR, never committed: {@code ./mvnw test -Dtest=MarketplaceFeedParserRealHarTest
 * -Dcarfinder.realHar=<path> [-Dcarfinder.realHar.min=2400] [-Dcarfinder.realHar.doc=true]}. Prints counts only.
 */
class MarketplaceFeedParserRealHarTest {

	@Test
	void realHar_countsMatchTheSpec() throws IOException {
		String path = System.getProperty("carfinder.realHar");
		assumeTrue(path != null && !path.isBlank(), "set -Dcarfinder.realHar=<path> to run");
		int min = Integer.getInteger("carfinder.realHar.min", 1);
		MarketplaceFeedParser parser = new MarketplaceFeedParser(new MarketplaceTitle(KnowledgeLoader.loadClasspath()),
				Places.loadClasspath());

		List<String> documents;
		MarketplaceFeedParser.FeedResult all;
		try (InputStream in = Files.newInputStream(Path.of(path)); Stream<String> bodies = HarReader.bodies(in)) {
			documents = bodies.filter(b -> b.stripLeading().startsWith("<")).toList();
		}
		try (InputStream in = Files.newInputStream(Path.of(path)); Stream<String> bodies = HarReader.bodies(in)) {
			all = parser.parse(bodies);
		}
		List<RawListing> live = all.live();
		int fromDocuments = documents.isEmpty() ? 0 : parser.parse(documents.stream()).live().size();
		System.out.printf("real HAR: live %d, unavailable %d, documents %d (live from documents %d), make %d, year %d,"
				+ " km %d, district %d, no price %d, no city %d%n", live.size(), all.unavailableIds().size(), documents.size(), fromDocuments,
				live.stream().filter(l -> l.make() != null).count(), live.stream().filter(l -> l.year() != null).count(),
				live.stream().filter(l -> l.km() != null).count(),
				live.stream().filter(l -> l.district() != null).count(),
				live.stream().filter(l -> l.priceEur() == null).count(), live.stream().filter(l -> l.city() == null).count());

		assertThat(live).hasSizeGreaterThanOrEqualTo(min);
		assertThat(live).allMatch(l -> l.priceEur() != null);
		// 6 of 2407 on the 2026-11-09 HAR have no location object; they stay, with "localização desconhecida"
		assertThat(live.stream().filter(l -> l.city() != null).count()).isGreaterThanOrEqualTo(live.size() * 99L / 100);
		if (Boolean.getBoolean("carfinder.realHar.doc")) {
			assertThat(fromDocuments).isPositive();
		}
	}
}
