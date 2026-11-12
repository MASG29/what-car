package carfinder.source.custojusto;

import static carfinder.source.custojusto.CustoJustoParserTest.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import carfinder.listing.RawListing;
import carfinder.source.Pace;
import carfinder.source.SearchQuery;
import carfinder.source.SoldOrReservedException;
import carfinder.source.SourceException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustoJustoSourceTest {

	private static final SearchQuery QUERY = new SearchQuery(2500, null, null);
	private static final String SEARCH = "/portugal/veiculos/carros-usados";
	private static final String AD = "/braga/veiculos/carros-usados/citadino/renault-clio-1-2-16v-90000001";

	private HttpServer server;
	private String base;
	private final List<String> requests = new CopyOnWriteArrayList<>();
	private final List<String> userAgents = new CopyOnWriteArrayList<>();
	private volatile String searchHtml;

	@BeforeEach
	void startServer() throws IOException {
		searchHtml = fixture("custojusto-search.html");
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", ex -> {
			requests.add(ex.getRequestURI().toString());
			userAgents.add(ex.getRequestHeaders().getFirst("User-Agent"));
			String path = ex.getRequestURI().getPath();
			int status = 200;
			String body = "";
			if (path.equals(SEARCH) || path.equals(SEARCH + "/renault/clio")) {
				body = searchHtml;
			}
			else if (path.equals(SEARCH + "/renault/naoexiste")) {
				status = 308;
				ex.getResponseHeaders().add("Location", SEARCH + "/renault/q/naoexiste?pe=2500");
			}
			else if (path.equals(AD)) {
				body = fixture("custojusto-advert.html");
			}
			else if (path.endsWith("-90000009")) {
				status = 307;
				ex.getResponseHeaders().add("Location", "/braga/veiculos/carros-usados/citadino?gone=90000009");
			}
			else if (path.endsWith("-90000008")) {
				status = 302;
				ex.getResponseHeaders().add("Location", "/somewhere");
			}
			else {
				status = 500;
				body = "nope";
			}
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
			if (bytes.length > 0) {
				ex.getResponseBody().write(bytes);
			}
			ex.close();
		});
		server.start();
		base = "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	private CustoJustoSource source(int maxPages) {
		return new CustoJustoSource(new CustoJustoProperties(base, maxPages, 7, 0, 0, "test-agent"),
				new Pace(0, 0, RandomGenerator.getDefault()), CustoJustoParserTest.PARSER,
				CustoJustoAdvertParserTest.PARSER);
	}

	@Test
	void pagesUntilDone() {
		List<String> progress = new CopyOnWriteArrayList<>();

		List<RawListing> result = source(10).search(QUERY, progress::add);

		// 120 ads, 40 per page: three pages, newest first is the site's default order
		assertThat(requests).containsExactly(SEARCH + "?ps=100&pe=2500", SEARCH + "?ps=100&pe=2500&o=2", SEARCH + "?ps=100&pe=2500&o=3");
		assertThat(result).extracting(RawListing::externalId).containsExactly("90000001", "90000002", "90000003");
		assertThat(progress).isNotEmpty();
		assertThat(userAgents).containsOnly("test-agent");
	}

	@Test
	void stopsAtMaxPages() {
		source(2).search(QUERY, p -> { });

		assertThat(requests).hasSize(2);
	}

	@Test
	void stopsOnEmptyPage() {
		searchHtml = searchHtml.replaceAll("\"listItems\":\\[.*?\\],\"initialState\"", "\"listItems\":[],\"initialState\"");

		assertThat(source(10).search(QUERY, p -> { })).isEmpty();
		assertThat(requests).hasSize(1);
	}

	@Test
	void search_withMakeModelAndYear_requestsModelPath() {
		source(1).search(new SearchQuery(2500, null, 2005, "Renault", "Clio"), p -> { });

		assertThat(requests).containsExactly(SEARCH + "/renault/clio?ps=100&pe=2500&rs=2005");
	}

	@Test
	void unknownModelSlug_fallsBackToSearchWithoutModel() {
		List<RawListing> result = source(1).search(new SearchQuery(2500, null, null, "Renault", "Naoexiste"), p -> { });

		assertThat(requests).containsExactly(SEARCH + "/renault/naoexiste?ps=100&pe=2500", SEARCH + "?ps=100&pe=2500");
		assertThat(result).hasSize(3);
	}

	@Test
	void details_fetchesAdvertPageAndParses() {
		RawListing r = source(1).details(base + AD);

		assertThat(r.externalId()).isEqualTo("90000001");
		assertThat(requests).containsExactly(AD);
	}

	@Test
	void goneAd_isSoldOrReserved() {
		assertThatThrownBy(() -> source(1).details(base + "/braga/veiculos/carros-usados/citadino/x-90000009"))
				.isInstanceOf(SoldOrReservedException.class).hasMessageContaining("CustoJusto");
	}

	@Test
	void otherRedirect_notFollowed() {
		assertThatThrownBy(() -> source(1).details(base + "/braga/veiculos/carros-usados/citadino/x-90000008"))
				.isInstanceOf(SourceException.class).isNotInstanceOf(SoldOrReservedException.class)
				.hasMessage("CustoJusto respondeu com erro HTTP 302");
		assertThat(requests).hasSize(1);
	}

	@Test
	void httpError_throwsWithStatus() {
		assertThatThrownBy(() -> source(1).details(base + "/porto/veiculos/carros-usados/sedan/boom-90000010"))
				.isInstanceOf(SourceException.class).hasMessage("CustoJusto respondeu com erro HTTP 500");
	}

	@Test
	void forbiddenPath_throws() {
		for (String path : List.of("/api/x", "/ajax/x", "/account/x", "/v2/auth", "/user/1", "/login", "/ar/1",
				"/ajuda-rules", "/braga/veiculos/carros-usados/../../api/x", "/braga/imoveis/x-1")) {
			assertThatThrownBy(() -> source(1).details(base + path)).as(path).isInstanceOf(IllegalStateException.class);
		}
		assertThat(requests).isEmpty();
	}

	@Test
	void otherSchemeHostPortOrQuery_throwsWithoutRequest() {
		int port = server.getAddress().getPort();
		for (String url : List.of("http://localhost:" + port + AD, "https://127.0.0.1:" + port + AD,
				"http://127.0.0.1:" + (port + 1) + AD, "http://user@127.0.0.1:" + port + AD, base + AD + "?x=1",
				base + AD + "#f")) {
			assertThatThrownBy(() -> source(1).details(url)).as(url).isInstanceOf(IllegalStateException.class);
		}
		assertThat(requests).isEmpty();
	}

	@Test
	void maxDetailsPerRun_fromConfig() {
		assertThat(source(1).maxDetailsPerRun()).isEqualTo(7);
	}
}
