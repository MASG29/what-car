package carfinder.source.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import carfinder.listing.RawListing;
import carfinder.source.NeedsUserException;
import carfinder.source.Pace;
import carfinder.source.SourceException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Headless Chromium against a local page that mimics the feed; nothing here reaches Facebook. */
class MarketplaceBrowserTest {

	/** The page's own script: every scroll grows the page and fetches one more feed body. */
	private static final String SCROLL_SCRIPT = """
			<div style="height:3000px"></div>
			<script>
			addEventListener('scroll', () => {
			  document.body.insertAdjacentHTML('beforeend', '<div style="height:3000px"></div>');
			  fetch('/api/graphql/', {method: 'POST', headers: {'X-From-Page': '1'}});
			});
			</script>""";

	private static String doc;
	private static String graphql;

	@TempDir
	Path profile;
	private HttpServer server;
	private String base;
	private MarketplaceBrowser browser;
	private final AtomicInteger searchLoads = new AtomicInteger();
	private final AtomicInteger loginLoads = new AtomicInteger();
	private final AtomicInteger graphqlByPage = new AtomicInteger();
	private final AtomicInteger graphqlOther = new AtomicInteger();
	private volatile boolean loginSetsCookie = true;
	private volatile String searchRedirect;
	private volatile String loginRedirect;
	private volatile String searchExtra = "";

	@BeforeAll
	static void fixtureBodies() throws IOException {
		JsonNode entries = JsonMapper.builder().build()
				.readTree(Files.readString(Path.of("src/test/resources/fixtures/marketplace-feed.har")))
				.path("log").path("entries");
		doc = entries.get(0).path("response").path("content").path("text").asString();
		graphql = entries.get(2).path("response").path("content").path("text").asString();
	}

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", this::handle);
		server.start();
		base = "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@AfterEach
	void stop() {
		if (browser != null) {
			browser.close();
		}
		server.stop(0);
	}

	private void handle(HttpExchange ex) throws IOException {
		String path = ex.getRequestURI().getPath();
		String body = "";
		if (path.startsWith("/marketplace/category/vehicles")) {
			searchLoads.incrementAndGet();
			if (searchRedirect != null) {
				redirect(ex, searchRedirect);
				return;
			}
			body = doc.replace("</body>", searchExtra + SCROLL_SCRIPT + "</body>");
		}
		else if (path.startsWith("/login")) {
			loginLoads.incrementAndGet();
			if (loginRedirect != null) {
				redirect(ex, loginRedirect);
				return;
			}
			if (loginSetsCookie) { // stands for Marco finishing the login himself
				ex.getResponseHeaders().add("Set-Cookie", "c_user=1; Path=/");
			}
			body = "<html><body><form><input name=\"email\"><input name=\"pass\" type=\"password\"></form></body></html>";
		}
		else if (path.equals("/api/graphql/")) {
			(ex.getRequestHeaders().containsKey("X-From-Page") ? graphqlByPage : graphqlOther).incrementAndGet();
			body = graphql;
		}
		else if (path.startsWith("/checkpoint")) {
			body = "<html><body>Confirma a tua identidade</body></html>";
		}
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
		ex.sendResponseHeaders(200, bytes.length == 0 ? -1 : bytes.length);
		if (bytes.length > 0) {
			ex.getResponseBody().write(bytes);
		}
		ex.close();
	}

	private void redirect(HttpExchange ex, String to) throws IOException {
		ex.getResponseHeaders().add("Location", base + to);
		ex.sendResponseHeaders(302, -1);
		ex.close();
	}

	private MarketplaceBrowser browser(int maxScrolls) {
		browser = new MarketplaceBrowser(profile.toString(), true, maxScrolls, new Pace(0, 0, RandomGenerator.getDefault()));
		return browser;
	}

	private String searchUrl() {
		return base + "/marketplace/category/vehicles?maxPrice=3000&exact=false";
	}

	/** First call opens the login page (which sets the session cookie here); the retry then loads the search. */
	private List<String> loggedInCollect(MarketplaceBrowser b) {
		assertThatThrownBy(() -> b.collect(searchUrl(), m -> {})).isInstanceOf(NeedsUserException.class);
		return b.collect(searchUrl(), m -> {});
	}

	@Test
	void collectsGraphqlBodiesWhileScrolling() {
		List<String> bodies = loggedInCollect(browser(2));

		assertThat(bodies).hasSize(3); // the page document (first batch) and one feed body per scroll
		List<RawListing> live = MarketplaceFeedParserTest.PARSER.parse(bodies.stream()).live();
		assertThat(live).extracting(RawListing::externalId).contains("1001", "1002", "1003");
		assertThat(browser.isOpen()).isFalse(); // the window closes after a run
	}

	@Test
	void stopsAtScrollBudget() {
		loggedInCollect(browser(3));

		assertThat(graphqlByPage.get()).isEqualTo(3);
		assertThat(searchLoads.get()).isEqualTo(1);
	}

	@Test
	void zeroScrolls_readsOnlyTheFirstBatch() {
		List<String> bodies = loggedInCollect(browser(0));

		assertThat(bodies).hasSize(1);
		assertThat(graphqlByPage.get()).isZero();
	}

	@Test
	void neverRequestsGraphqlItself() {
		loggedInCollect(browser(2));

		assertThat(graphqlOther.get()).isZero();
		assertThat(graphqlByPage.get()).isEqualTo(2);
	}

	@Test
	void pageKeepsFetching_whileBodiesAreRead() {
		// Facebook's page fetches on its own all the time; responses arriving while bodies are read must not break the run
		searchExtra = "<script>setInterval(() => fetch('/api/graphql/', {method: 'POST', headers: {'X-From-Page': '1'}}), 5)</script>";

		List<String> bodies = loggedInCollect(browser(2));

		assertThat(bodies.size()).isGreaterThan(1);
		assertThat(browser.isOpen()).isFalse();
	}

	@Test
	void unexpectedError_closesTheWindow() {
		MarketplaceBrowser b = browser(2);
		assertThatThrownBy(() -> b.collect(searchUrl(), m -> {})).isInstanceOf(NeedsUserException.class);

		assertThatThrownBy(() -> b.collect(searchUrl(), m -> {
			throw new IllegalStateException("boom");
		})).isInstanceOf(IllegalStateException.class);
		assertThat(b.isOpen()).isFalse();
	}

	@Test
	void screenshotDir_savesThePage(@TempDir Path shots) throws IOException {
		MarketplaceBrowser b = browser(1);
		b.setScreenshotDir(shots.toString());

		loggedInCollect(b);

		try (var files = Files.list(shots)) {
			assertThat(files).singleElement().satisfies(f -> assertThat(f.toString()).endsWith(".png"));
		}
	}

	@Test
	void loginPage_needsUser() {
		loginSetsCookie = false;
		MarketplaceBrowser b = browser(2);

		assertThatThrownBy(() -> b.collect(searchUrl(), m -> {})).isInstanceOf(NeedsUserException.class)
				.hasMessage("Faz login na janela aberta");
		// the retry while Marco types must not reload anything
		assertThatThrownBy(() -> b.collect(searchUrl(), m -> {})).isInstanceOf(NeedsUserException.class);
		assertThat(loginLoads.get()).isEqualTo(1);
		assertThat(searchLoads.get()).isZero();
		assertThat(b.isOpen()).isTrue(); // the window stays for the login
	}

	@Test
	void appShutdown_closesTheLoginWindow() {
		loginSetsCookie = false;
		try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
			context.registerBean(MarketplaceBrowser.class, () -> browser(2));
			context.refresh();
			MarketplaceBrowser b = context.getBean(MarketplaceBrowser.class);
			assertThatThrownBy(() -> b.collect(searchUrl(), m -> {})).isInstanceOf(NeedsUserException.class);
			assertThat(b.isOpen()).isTrue();

			context.close();

			assertThat(b.isOpen()).isFalse();
		}
	}

	@Test
	void searchRedirectsToLogin_needsUser() {
		MarketplaceBrowser b = browser(2);
		assertThatThrownBy(() -> b.collect(searchUrl(), m -> {})).isInstanceOf(NeedsUserException.class);
		searchRedirect = "/login/?next=x";

		assertThatThrownBy(() -> b.collect(searchUrl(), m -> {})).isInstanceOf(NeedsUserException.class);
		assertThat(b.isOpen()).isTrue();
	}

	@Test
	void checkpointUrl_failsAndCloses() {
		MarketplaceBrowser b = browser(2);
		assertThatThrownBy(() -> b.collect(searchUrl(), m -> {})).isInstanceOf(NeedsUserException.class);
		searchRedirect = "/checkpoint/123/";

		assertThatThrownBy(() -> b.collect(searchUrl(), m -> {})).isInstanceOf(SourceException.class)
				.hasMessage("O Facebook pediu uma verificação; a pesquisa automática parou");
		assertThat(b.isOpen()).isFalse();
	}

	@Test
	void checkpointDuringLogin_failsAndCloses() {
		loginRedirect = "/checkpoint/block/";
		MarketplaceBrowser b = browser(2);

		assertThatThrownBy(() -> b.collect(searchUrl(), m -> {})).isInstanceOf(SourceException.class)
				.hasMessage("O Facebook pediu uma verificação; a pesquisa automática parou");
		assertThat(b.isOpen()).isFalse();
		assertThat(searchLoads.get()).isZero();
	}

	@Test
	void checkpointForm_failsAndCloses() {
		searchExtra = "<form action=\"/checkpoint/block/\"><button>Continuar</button></form>";
		MarketplaceBrowser b = browser(2);
		assertThatThrownBy(() -> b.collect(searchUrl(), m -> {})).isInstanceOf(NeedsUserException.class);

		assertThatThrownBy(() -> b.collect(searchUrl(), m -> {})).isInstanceOf(SourceException.class)
				.hasMessage("O Facebook pediu uma verificação; a pesquisa automática parou");
		assertThat(b.isOpen()).isFalse();
		assertThat(graphqlByPage.get()).isZero(); // stopped before any scroll
	}
}
