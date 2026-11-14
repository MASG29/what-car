package carfinder.source.olx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import carfinder.geo.Places;
import carfinder.knowledge.Generation;
import carfinder.knowledge.KnowledgeBase;
import carfinder.knowledge.KnowledgeLoader;
import carfinder.listing.RawListing;
import carfinder.source.FeedResult;
import carfinder.source.HarReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Local check against a real OLX HAR, never committed: {@code ./mvnw test -Dtest=OlxFeedParserRealHarTest
 * -Dcarfinder.olxHar=<path> [-Dcarfinder.olxHar.min=190]}. Prints counts only.
 */
class OlxFeedParserRealHarTest {

	@Test
	void realHar_counts() throws IOException {
		String path = System.getProperty("carfinder.olxHar");
		assumeTrue(path != null && !path.isBlank(), "set -Dcarfinder.olxHar=<path> to run");
		int min = Integer.getInteger("carfinder.olxHar.min", 1);
		KnowledgeBase kb = KnowledgeLoader.loadClasspath();
		OlxFeedParser parser = new OlxFeedParser(kb, Places.loadClasspath());

		FeedResult all;
		try (InputStream in = Files.newInputStream(Path.of(path)); Stream<String> bodies = HarReader.bodies(in, OlxFeedParser.HAR)) {
			all = parser.parse(bodies);
		}
		FeedResult noDocument;
		try (InputStream in = Files.newInputStream(Path.of(path)); Stream<String> bodies = HarReader.bodies(in, OlxFeedParser.HAR)) {
			noDocument = parser.parse(bodies.filter(b -> !b.stripLeading().startsWith("<")));
		}
		List<RawListing> live = all.live();
		long byTitle = 0;
		long disagree = 0;
		for (RawListing l : live) {
			Optional<Generation> t = kb.recogniseMakeModel(l.title());
			byTitle += t.isPresent() ? 1 : 0;
			if (t.isPresent() && l.make() != null
					&& !(t.get().make().equals(l.make()) && t.get().model().equals(l.model()))) {
				disagree++;
			}
		}
		System.out.printf("OLX HAR: live %d, unavailable %d, skipped (Standvirtual) %d, recognised %d, by title %d,"
				+ " title disagrees %d, recognised without document %d/%d, year %d, km %d, district %d, cc %d,"
				+ " description %d, no price %d, no city %d%n", live.size(), all.unavailableIds().size(),
				all.skipped(), count(live, RawListing::make), byTitle, disagree,
				count(noDocument.live(), RawListing::make), noDocument.live().size(), count(live, RawListing::year),
				count(live, RawListing::km), count(live, RawListing::district), count(live, RawListing::cc),
				count(live, RawListing::description), live.size() - count(live, RawListing::priceEur),
				live.size() - count(live, RawListing::city));

		assertThat(live).hasSizeGreaterThanOrEqualTo(min);
		assertThat(live).allMatch(l -> l.priceEur() != null && l.city() != null);
	}

	private static long count(List<RawListing> ls, java.util.function.Function<RawListing, ?> f) {
		return ls.stream().map(f).filter(Objects::nonNull).count();
	}
}
