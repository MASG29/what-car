package carfinder.source.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import carfinder.source.SourceException;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class HarReaderTest {

	static final Path FIXTURE = Path.of("src/test/resources/fixtures/marketplace-feed.har");

	static List<String> bodies(Path har) throws IOException {
		try (InputStream in = Files.newInputStream(har); Stream<String> s = HarReader.bodies(in)) {
			return s.toList();
		}
	}

	@Test
	void yieldsOnlyGraphqlAndMarketplaceDocuments() throws IOException {
		List<String> bodies = bodies(FIXTURE);

		// the Marketplace document and two graphql POSTs; not the script, not /ajax/
		assertThat(bodies).hasSize(3);
		assertThat(bodies.getFirst()).startsWith("<!DOCTYPE html>");
		assertThat(bodies.get(1)).startsWith("for (;;);");
		// request/response headers and cookies are never read
		assertThat(String.join("", bodies)).doesNotContain("FAKECOOKIE123", "FAKESECRETXS", "FAKESETCOOKIE",
				"FAKEAUTHHEADER", "FAKETOKEN");
	}

	@Test
	void olxFilter_yieldsOnlyGraphqlAndSearchDocument() throws IOException {
		List<String> bodies;
		try (InputStream in = Files.newInputStream(Path.of("src/test/resources/fixtures/olx-feed.har"));
				Stream<String> s = HarReader.bodies(in, carfinder.source.olx.OlxFeedParser.HAR)) {
			bodies = s.toList();
		}

		// the search document and the graphql POST; not the script, not /api/v1/
		assertThat(bodies).hasSize(2);
		assertThat(bodies.getFirst()).startsWith("<!DOCTYPE html>");
		assertThat(bodies.get(1)).startsWith("{\"data\"");
		assertThat(String.join("", bodies)).doesNotContain("FAKEOLXCOOKIE", "FAKEOLXTOKEN", "FAKEOLXAUTH",
				"FAKEOLXSETCOOKIE");
	}

	@Test
	void olxFilter_skipsFacebookBodies() throws IOException {
		try (InputStream in = Files.newInputStream(FIXTURE);
				Stream<String> s = HarReader.bodies(in, carfinder.source.olx.OlxFeedParser.HAR)) {
			assertThat(s.toList()).isEmpty();
		}
	}

	@Test
	void notAHar_throwsTheFiltersMessage() {
		assertThatThrownBy(() -> HarReader.bodies(stream("hello"), carfinder.source.olx.OlxFeedParser.HAR).toList())
				.isInstanceOf(SourceException.class).hasMessage(carfinder.source.olx.OlxFeedParser.NO_ADS);
	}

	@Test
	void decodesBase64() throws IOException {
		assertThat(bodies(FIXTURE).get(2)).contains("2009 Fiat Grande Punto 1.2 gasolina");
	}

	@Test
	void notAHar_throwsFilterMessage() {
		assertThatThrownBy(() -> HarReader.bodies(stream("{\"hello\":1}")).toList())
				.isInstanceOf(SourceException.class).hasMessage(MarketplaceFeedParser.NO_ADS);
	}

	@Test
	void importsLargeHarFromStream() {
		String head = "{\"log\":{\"version\":\"1.2\",\"entries\":[" + graphqlEntry() + ",";
		String padding = "{\"request\":{\"method\":\"GET\",\"url\":\"https://static.example/x.js\",\"headers\":[]},"
				+ "\"response\":{\"content\":{\"mimeType\":\"text/javascript\",\"text\":\"" + "x".repeat(1000)
				+ "\"}}},";
		int copies = 61_000; // ~ 61 MB of entries after the first one
		java.util.Enumeration<InputStream> parts = new java.util.Enumeration<>() {
			int i = -1;

			public boolean hasMoreElements() {
				return i <= copies;
			}

			public InputStream nextElement() {
				i++;
				if (i == 0) {
					return stream(head);
				}
				return stream(i <= copies ? padding : graphqlEntry() + "]}}");
			}
		};
		CountingStream in = new CountingStream(new SequenceInputStream(parts));

		try (Stream<String> s = HarReader.bodies(in)) {
			Iterator<String> it = s.iterator();
			assertThat(it.next()).contains("lazy-item");
			in.firstItemSeen = true;
			assertThat(in.read).isLessThan(8L << 20);
			int rest = 0;
			while (it.hasNext()) {
				it.next();
				rest++;
			}
			assertThat(rest).isEqualTo(1);
			assertThat(in.read).isGreaterThan(60L << 20);
		}
	}

	private static String graphqlEntry() {
		return "{\"request\":{\"method\":\"POST\",\"url\":\"https://www.facebook.com/api/graphql/\"},"
				+ "\"response\":{\"content\":{\"mimeType\":\"text/html\",\"text\":\"{\\\"lazy-item\\\":1}\"}}}";
	}

	private static InputStream stream(String s) {
		return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
	}

	/** Fails once more than 8 MB were read before the first body came out. */
	private static final class CountingStream extends FilterInputStream {
		long read;
		boolean firstItemSeen;

		CountingStream(InputStream in) {
			super(in);
		}

		@Override
		public int read() throws IOException {
			int b = super.read();
			count(b < 0 ? 0 : 1);
			return b;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			int n = super.read(b, off, len);
			count(Math.max(n, 0));
			return n;
		}

		private void count(int n) throws IOException {
			read += n;
			if (!firstItemSeen && read > (8L << 20)) {
				throw new IOException("read more than 8 MB before the first body");
			}
		}
	}
}
