package carfinder.source.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import carfinder.listing.ListingStore;
import carfinder.listing.RawListing;
import carfinder.listing.Site;
import carfinder.source.Pace;
import carfinder.source.SearchQuery;
import carfinder.source.SourceException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarketplaceSourceTest {

	private HttpServer server;
	private String base;
	private final List<String> requests = new CopyOnWriteArrayList<>();
	private final List<Headers> headers = new CopyOnWriteArrayList<>();
	private final AtomicInteger paces = new AtomicInteger();
	@TempDir
	Path profile;
	private MarketplaceBrowser browser;
	private final ListingStore store = mock(ListingStore.class);

	@BeforeEach
	void realBrowser() {
		browser = new MarketplaceBrowser(profile.toString(), true, 1, new Pace(0, 0, RandomGenerator.getDefault()));
	}

	@BeforeEach
	void startServer() throws IOException {
		String advert = MarketplaceAdvertParserTest.fixture();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", ex -> {
			requests.add(ex.getRequestURI().toString());
			headers.add(ex.getRequestHeaders());
			String path = ex.getRequestURI().getPath();
			int status = 200;
			String body = "";
			// a real visit gets anonymous cookies; the source must never send them back
			ex.getResponseHeaders().add("Set-Cookie", "datr=fake-anon-cookie; Path=/; Domain=127.0.0.1");
			if (path.equals("/marketplace/item/9000000000000001/")) {
				body = advert;
			}
			else if (path.equals("/marketplace/item/2/")) {
				status = 302;
				ex.getResponseHeaders().add("Location", base + "/login/?next=x");
			}
			else if (path.equals("/marketplace/item/3/")) {
				status = 302;
				ex.getResponseHeaders().add("Location", base + "/marketplace/");
			}
			else {
				status = 404;
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

	private MarketplaceSource source() {
		Pace pace = new Pace(0, 0, RandomGenerator.getDefault()) {
			@Override
			public void between() {
				paces.incrementAndGet();
			}
		};
		return new MarketplaceSource(new MarketplaceProperties(base, 20, 0, 0, "test-agent"), pace,
				MarketplaceAdvertParserTest.PARSER, MarketplaceFeedParserTest.PARSER, browser, store);
	}

	private String item(String id) {
		return base + "/marketplace/item/" + id + "/";
	}

	@Test
	void details_readsPageWithBrowserHeaders() {
		RawListing r = source().details(item("9000000000000001"));

		assertThat(r.site()).isEqualTo(Site.MARKETPLACE);
		assertThat(r.description()).contains("Gasóleo");
		Headers h = headers.getFirst();
		assertThat(h.getFirst("User-Agent")).isEqualTo("test-agent");
		assertThat(h.getFirst("Sec-Fetch-Mode")).isEqualTo("navigate");
		assertThat(h.getFirst("Sec-Fetch-Dest")).isEqualTo("document");
		assertThat(h.getFirst("Accept")).startsWith("text/html");
	}

	@Test
	void noCookiesSent() {
		MarketplaceSource s = source();

		s.details(item("9000000000000001"));
		s.details(item("9000000000000001"));

		assertThat(headers).hasSize(2).allSatisfy(h -> assertThat(h.containsKey("Cookie")).isFalse());
	}

	@Test
	void paceBetweenRequests_notBeforeFirst() {
		MarketplaceSource s = source();

		s.details(item("9000000000000001"));
		assertThat(paces).hasValue(0);
		s.details(item("9000000000000001"));
		assertThat(paces).hasValue(1);
	}

	@Test
	void loginRedirect_throwsPt() {
		assertThatThrownBy(() -> source().details(item("2"))).isInstanceOf(LoginWallException.class)
				.hasMessage("O Facebook não mostra este anúncio sem login; fica com os dados importados");
		assertThat(requests).containsExactly("/marketplace/item/2/");
	}

	@Test
	void otherRedirectOrError_isPlainSourceException() {
		assertThatThrownBy(() -> source().details(item("3"))).isInstanceOf(SourceException.class)
				.isNotInstanceOf(LoginWallException.class).hasMessageContaining("302");
		assertThatThrownBy(() -> source().details(item("4"))).isInstanceOf(SourceException.class)
				.isNotInstanceOf(LoginWallException.class).hasMessageContaining("404");
	}

	@Test
	void foreignHost_rejected() {
		int port = server.getAddress().getPort();
		for (String url : List.of("http://localhost:" + port + "/marketplace/item/1/",
				"https://127.0.0.1:" + port + "/marketplace/item/1/",
				"http://127.0.0.1:" + (port + 1) + "/marketplace/item/1/",
				"http://user@127.0.0.1:" + port + "/marketplace/item/1/",
				base + "/api/graphql/",
				base + "/marketplace/item/abc/",
				base + "/marketplace/item/1/../../api/graphql/",
				base + "/marketplace/item/1/?ref=x",
				base + "/login/")) {
			assertThatThrownBy(() -> source().details(url)).as(url).isInstanceOf(IllegalStateException.class);
		}
		assertThat(requests).isEmpty();
	}

	@Test
	void searchReadsNothing_andBudgetIsConfigured() {
		assertThat(source().search(new SearchQuery(2500, null, null), p -> { })).isEmpty();
		assertThat(source().maxDetailsPerRun()).isEqualTo(20);
		assertThat(source().site()).isEqualTo(Site.MARKETPLACE);
		assertThat(requests).isEmpty();
	}

	@Test
	void noBudgetConfigured_readsEveryPage() {
		MarketplaceSource s = new MarketplaceSource(new MarketplaceProperties(base, null, 0, 0, "test-agent"),
				new Pace(0, 0, RandomGenerator.getDefault()), MarketplaceAdvertParserTest.PARSER,
				MarketplaceFeedParserTest.PARSER, browser, store);

		assertThat(s.maxDetailsPerRun()).isEqualTo(Integer.MAX_VALUE);
	}

	@Test
	void automatedFalse_neverOpensBrowser() throws IOException {
		assertThat(source().search(new SearchQuery(2500, null, null, "Toyota", "Yaris"), p -> { })).isEmpty();

		assertThat(browser.isOpen()).isFalse();
		try (var files = Files.list(profile)) {
			assertThat(files).isEmpty(); // no browser was ever started on the profile
		}
	}

	@Test
	void automated_opensTheSearchLinkAndParsesTheFeed() throws IOException {
		browser = mock(MarketplaceBrowser.class);
		String doc = HarReaderTest.bodies(HarReaderTest.FIXTURE).getFirst();
		when(browser.collect(anyString(),
				any())).thenReturn(List.of(doc));

		List<RawListing> found = source().search(new SearchQuery(2500, null, null, "Toyota", "Yaris", true), p -> { });

		assertThat(found).extracting(RawListing::externalId).containsExactlyInAnyOrder("1001", "1002");
		verify(browser).collect(eq(
				base + "/marketplace/category/vehicles?maxPrice=2500&query=Toyota+Yaris&exact=false"),
				any());
		assertThat(requests).isEmpty(); // the source itself requests nothing
	}

	@Test
	void automated_marksSoldAndReservedUnavailable() throws IOException {
		browser = mock(MarketplaceBrowser.class);
		when(browser.collect(anyString(), any())).thenReturn(HarReaderTest.bodies(HarReaderTest.FIXTURE));

		List<RawListing> found = source().search(new SearchQuery(2500, null, null, null, null, true), p -> { });

		verify(store).markUnavailable(Site.MARKETPLACE, "1004");
		verify(store).markUnavailable(Site.MARKETPLACE, "1005");
		assertThat(found).extracting(RawListing::externalId).doesNotContain("1004", "1005").isNotEmpty();
	}

	@Test
	void automatedWithNothingCollected_saysSo() {
		browser = mock(MarketplaceBrowser.class);
		when(browser.collect(anyString(),
				any())).thenReturn(List.of());

		assertThatThrownBy(() -> source().search(new SearchQuery(2500, null, null, null, null, true), p -> { }))
				.isInstanceOf(SourceException.class)
				.hasMessage("A pesquisa automática não recebeu anúncios do Marketplace");
	}
}
