package carfinder.source.standvirtual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import carfinder.listing.RawListing;
import carfinder.source.Pace;
import carfinder.source.SearchQuery;
import carfinder.source.SourceException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StandvirtualSourceTest {

	private static final SearchQuery QUERY = new SearchQuery(2500, null, null);

	private HttpServer server;
	private String base;
	private final List<String> requests = new CopyOnWriteArrayList<>();
	private final List<String> userAgents = new CopyOnWriteArrayList<>();
	private volatile String searchHtml;
	private volatile boolean emptyAfterFirstPage;

	private static String fixture(String name) throws IOException {
		try (InputStream in = StandvirtualSourceTest.class.getResourceAsStream("/fixtures/" + name)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@BeforeEach
	void startServer() throws IOException {
		searchHtml = fixture("standvirtual-search.html");
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", ex -> {
			requests.add(ex.getRequestURI().toString());
			userAgents.add(ex.getRequestHeaders().getFirst("User-Agent"));
			String path = ex.getRequestURI().getPath();
			int status = 200;
			String body;
			if (path.equals("/carros")) {
				String q = ex.getRequestURI().getQuery();
				boolean later = emptyAfterFirstPage && q != null && q.matches(".*page=([2-9]|\\d\\d+).*");
				body = later ? withoutEdges(searchHtml) : searchHtml;
			}
			else if (path.startsWith("/carros/anuncio/")) {
				body = fixture("standvirtual-advert.html");
			}
			else if (path.equals("/moved")) {
				status = 302;
				body = "";
				ex.getResponseHeaders().add("Location", base + "/carros/anuncio/renault-clio-IDfake0002.html");
			}
			else {
				status = path.startsWith("/gone") ? 404 : 500;
				body = "nope";
			}
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			ex.sendResponseHeaders(status, bytes.length);
			ex.getResponseBody().write(bytes);
			ex.close();
		});
		server.start();
		base = "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	private static String withoutEdges(String html) {
		int start = html.indexOf("edges\\\": [");
		int end = html.indexOf("], \\\"topads");
		return html.substring(0, start) + "edges\\\": [" + html.substring(end);
	}

	private StandvirtualSource source(int maxPages) {
		return new StandvirtualSource(
				new StandvirtualProperties(base, maxPages, 0, 0, "test-agent"),
				new Pace(0, 0, RandomGenerator.getDefault()));
	}

	private static long searchRequests(List<String> requests) {
		return requests.stream().filter(r -> r.startsWith("/carros?")).count();
	}

	@Test
	void details_fetchesAdvertPageAndParses() {
		String url = base + "/carros/anuncio/renault-clio-ver-1-5-dci-dynamique-IDfake0002.html";

		RawListing listing = source(5).details(url);

		assertThat(listing.externalId()).isNotBlank();
		assertThat(listing.description()).isNotNull();
		assertThat(requests).hasSize(1);
		assertThat(userAgents).containsExactly("test-agent");
	}

	@Test
	void pagesUntilTotalCountReached() {
		searchHtml = searchHtml.replace("totalCount\\\": 629", "totalCount\\\": 70");
		List<String> progress = new ArrayList<>();

		source(25).search(QUERY, progress::add);

		assertThat(searchRequests(requests)).isEqualTo(3);
		assertThat(requests).anyMatch(r -> r.contains("page=3"));
		assertThat(progress).isNotEmpty();
	}

	@Test
	void stopsPagingOnEmptySearchPage() {
		emptyAfterFirstPage = true;

		List<RawListing> result = source(25).search(QUERY, p -> { });

		assertThat(searchRequests(requests)).isEqualTo(2);
		assertThat(result).isNotEmpty();
	}

	@Test
	void stopsAtMaxPages() {
		source(2).search(QUERY, p -> { });

		assertThat(searchRequests(requests)).isEqualTo(2);
	}

	@Test
	void dedupesRepeatedIds() {
		searchHtml = searchHtml.replace("totalCount\\\": 629", "totalCount\\\": 70");

		List<RawListing> result = source(25).search(QUERY, p -> { });

		assertThat(result).hasSize(3);
		assertThat(result).extracting(RawListing::externalId).doesNotHaveDuplicates();
	}

	@Test
	void forbiddenPath_throws() {
		for (String path : List.of("/api/x", "/ajax/x", "/account/x", "/ad/contact/x")) {
			assertThatThrownBy(() -> source(5).details(base + path))
					.isInstanceOf(IllegalStateException.class);
		}
		assertThat(requests).isEmpty();
	}

	@Test
	void otherSchemeHostOrPort_throwsWithoutRequest() {
		int port = server.getAddress().getPort();
		for (String url : List.of("http://localhost:" + port + "/carros/anuncio/x", "https://127.0.0.1:" + port
				+ "/carros/anuncio/x", "http://127.0.0.1:" + (port + 1) + "/carros/anuncio/x",
				"http://example.test/carros/anuncio/x")) {
			assertThatThrownBy(() -> source(5).details(url)).as(url).isInstanceOf(IllegalStateException.class);
		}
		assertThat(requests).isEmpty();
	}

	@Test
	void redirect_notFollowed() {
		assertThatThrownBy(() -> source(5).details(base + "/moved"))
				.isInstanceOf(SourceException.class)
				.hasMessageContaining("302");
		assertThat(requests).containsExactly("/moved");
	}

	@Test
	void httpError_throwsSourceExceptionWithStatus() {
		assertThatThrownBy(() -> source(5).details(base + "/gone/1"))
				.isInstanceOf(SourceException.class)
				.hasMessageContaining("404");
		assertThatThrownBy(() -> source(5).details(base + "/boom"))
				.isInstanceOf(SourceException.class)
				.hasMessageContaining("500");
		assertThat(requests).hasSize(2);
	}
}
