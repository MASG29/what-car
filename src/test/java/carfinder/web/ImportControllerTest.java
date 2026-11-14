package carfinder.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import carfinder.TestcontainersConfiguration;
import carfinder.geo.Places;
import carfinder.knowledge.KnowledgeLoader;
import carfinder.listing.Listing;
import carfinder.listing.ListingRepository;
import carfinder.listing.ListingStore;
import carfinder.listing.Site;
import carfinder.search.SearchService;
import carfinder.source.marketplace.MarketplaceFeedParser;
import carfinder.source.marketplace.MarketplaceTitle;
import carfinder.source.olx.OlxFeedParser;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@DataJpaTest
@Import({TestcontainersConfiguration.class, ListingStore.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ExtendWith(OutputCaptureExtension.class)
class ImportControllerTest {

	private static final Path FIXTURE = Path.of("src/test/resources/fixtures/marketplace-feed.har");
	private static final List<String> SECRETS = List.of("Vendedor Teste", "990000000000999", "FAKECOOKIE123", "FAKESECRETXS",
			"FAKESETCOOKIE", "FAKEAUTHHEADER", "FAKETOKEN");

	@Autowired
	ListingStore store;
	@Autowired
	ListingRepository listings;
	@Autowired
	org.springframework.jdbc.core.JdbcTemplate jdbc;

	private final SearchService search = mock(SearchService.class);
	private MockMvc mvc;
	private carfinder.data.DataStore data;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		jdbc.update("DELETE FROM import_batch");
		listings.deleteAll();
		when(search.whileIdle(any())).thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(0)).get());
		MarketplaceFeedParser parser = new MarketplaceFeedParser(
				new MarketplaceTitle(KnowledgeLoader.loadClasspath()),
				new Places(Paths.get("src/test/resources/fixtures/geo/places-test.tsv")));
		data = new carfinder.data.DataStore(jdbc, java.time.Clock.systemUTC());
		OlxFeedParser olx = new OlxFeedParser(KnowledgeLoader.loadClasspath(),
				new Places(Paths.get("src/test/resources/fixtures/geo/places-test.tsv")));
		mvc = MockMvcBuilders.standaloneSetup(new ImportController(parser, olx, store, listings, search, data)).build();
	}

	@AfterEach
	void tearDown() {
		jdbc.update("DELETE FROM import_batch");
		listings.deleteAll(); // writes commit here; other test classes share the database
	}

	private String importFixture() throws Exception {
		return mvc.perform(post("/api/imports/marketplace").contentType(MediaType.APPLICATION_OCTET_STREAM)
				.content(Files.readAllBytes(FIXTURE))).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
	}

	@Test
	void importsFixture_returnsCounts() throws Exception {
		mvc.perform(post("/api/imports/marketplace").contentType(MediaType.APPLICATION_OCTET_STREAM)
				.content(Files.readAllBytes(FIXTURE))).andExpect(status().isOk())
				.andExpect(jsonPath("$.read").value(6)).andExpect(jsonPath("$.added").value(4))
				.andExpect(jsonPath("$.unavailable").value(2));
		assertThat(listings.findBySite(Site.MARKETPLACE)).hasSize(4);

		mvc.perform(post("/api/imports/marketplace").contentType(MediaType.APPLICATION_OCTET_STREAM)
				.content(Files.readAllBytes(FIXTURE))).andExpect(status().isOk())
				.andExpect(jsonPath("$.read").value(6)).andExpect(jsonPath("$.added").value(0));
	}

	@Test
	void importRecordsBatch() throws Exception {
		importFixture();
		importFixture();

		assertThat(data.imports()).hasSize(2).allSatisfy(b -> {
			assertThat(b.site()).isEqualTo(Site.MARKETPLACE);
			assertThat(b.read()).isEqualTo(6);
			assertThat(b.listings()).isEqualTo(4);
		});
		assertThat(data.imports()).extracting(b -> b.added()).containsExactly(0, 4);
	}

	@Test
	void liveAdSeenSoldLater_markedUnavailable() throws Exception {
		importFixture();
		// items sit inside JSON strings in the HAR, so their quotes are escaped; the base64 body (1006) stays as is
		String sold = Files.readString(FIXTURE).replace("\\\"is_sold\\\": false", "\\\"is_sold\\\": true");

		mvc.perform(post("/api/imports/marketplace").contentType(MediaType.APPLICATION_OCTET_STREAM)
				.content(sold)).andExpect(status().isOk()).andExpect(jsonPath("$.unavailable").value(5))
				.andExpect(jsonPath("$.added").value(0));

		assertThat(listings.findBySite(Site.MARKETPLACE)).hasSize(4)
				.allMatch(l -> l.isUnavailable() == !l.getExternalId().equals("1006"));
	}

	@Test
	void sellerNameNeverStoredOrLogged(CapturedOutput output) throws Exception {
		String result = importFixture();

		for (Listing l : listings.findAll()) {
			assertThat(String.join("|", String.valueOf(l.getTitle()), l.getUrl(), String.valueOf(l.getCity()),
					String.valueOf(l.getDistrict()), String.valueOf(l.getMake()), String.valueOf(l.getModel()),
					String.valueOf(l.getVersion()), String.valueOf(l.getDescription()), l.getExternalId()))
					.doesNotContain(SECRETS);
		}
		assertThat(result).doesNotContain(SECRETS);
		assertThat(output.getAll()).doesNotContain(SECRETS);
	}

	@Test
	void notHar_400Pt() throws Exception {
		for (String body : List.of("hello", "{}", "{\"log\":{\"entries\":[]}}", "")) {
			mvc.perform(post("/api/imports/marketplace").contentType(MediaType.APPLICATION_OCTET_STREAM)
					.content(body)).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.messagePt").value(MarketplaceFeedParser.NO_ADS));
		}
	}

	@Test
	void runInProgress_409() throws Exception {
		doThrow(new IllegalStateException("Já há uma pesquisa a decorrer")).when(search).whileIdle(any());

		mvc.perform(post("/api/imports/marketplace").contentType(MediaType.APPLICATION_OCTET_STREAM)
				.content(Files.readAllBytes(FIXTURE))).andExpect(status().isConflict())
				.andExpect(jsonPath("$.messagePt").value("Já há uma pesquisa a decorrer"));
	}

	@Test
	void importsWithoutTempFile() {
		// The raw body is streamed from the request: no multipart, so the servlet container writes no temp file.
		Method[] handlers = Arrays.stream(ImportController.class.getDeclaredMethods())
				.filter(m -> m.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class))
				.toArray(Method[]::new);
		assertThat(handlers).hasSize(2).allSatisfy(h -> assertThat(h.getParameterTypes())
				.containsExactly(HttpServletRequest.class).doesNotContain(MultipartFile.class));
	}

	private static final Path OLX_FIXTURE = Path.of("src/test/resources/fixtures/olx-feed.har");

	private String importOlx() throws Exception {
		return mvc.perform(post("/api/imports/olx").contentType(MediaType.APPLICATION_OCTET_STREAM)
				.content(Files.readAllBytes(OLX_FIXTURE))).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
	}

	@Test
	void importsOlxFixture_returnsCounts() throws Exception {
		mvc.perform(post("/api/imports/olx").contentType(MediaType.APPLICATION_OCTET_STREAM)
				.content(Files.readAllBytes(OLX_FIXTURE))).andExpect(status().isOk())
				.andExpect(jsonPath("$.read").value(3)).andExpect(jsonPath("$.added").value(2))
				.andExpect(jsonPath("$.unavailable").value(1)).andExpect(jsonPath("$.skipped").value(2));
		assertThat(listings.findBySite(Site.OLX)).hasSize(2);
		assertThat(listings.findBySite(Site.MARKETPLACE)).isEmpty();

		importOlx();
		assertThat(data.imports()).extracting(b -> b.site()).containsOnly(Site.OLX);
		assertThat(data.imports()).extracting(b -> b.added()).containsExactly(0, 2);
	}

	@Test
	void olxImport_storesTheFeedsDetailFields() throws Exception {
		importOlx();

		Listing l = listings.findBySiteAndExternalId(Site.OLX, "900001").orElseThrow();
		assertThat(l.getDescription()).isEqualTo("Bom estado.\nNão é para peças.");
		assertThat(l.getCc()).isEqualTo(1149);
		assertThat(l.getPowerCv()).isEqualTo(75);
		assertThat(l.getDoors()).isEqualTo(5);
		assertThat(l.getMonth()).isEqualTo(3);
		assertThat(l.getPhotoCount()).isEqualTo(6);
		assertThat(l.getKm()).isEqualTo(216000);
		assertThat(l.isParts()).isFalse();

		mvc.perform(post("/api/imports/olx").contentType(MediaType.APPLICATION_OCTET_STREAM)
				.content(Files.readString(OLX_FIXTURE).replace("Não é para peças.", "Vendo para peças.")))
				.andExpect(status().isOk());
		assertThat(listings.findBySiteAndExternalId(Site.OLX, "900001").orElseThrow().isParts()).isTrue();
	}

	@Test
	void olxLiveAdSeenRemovedLater_markedUnavailable() throws Exception {
		importOlx();
		String removed = Files.readString(OLX_FIXTURE).replace("\\\"status\\\": \\\"active\\\"",
				"\\\"status\\\": \\\"removed_by_user\\\"");

		mvc.perform(post("/api/imports/olx").contentType(MediaType.APPLICATION_OCTET_STREAM).content(removed))
				.andExpect(status().isOk());

		assertThat(listings.findBySite(Site.OLX)).extracting(Listing::getExternalId, Listing::isUnavailable)
				.contains(org.assertj.core.groups.Tuple.tuple("900003", true));
	}

	@Test
	void olxSellerDataNeverStoredOrLogged(CapturedOutput output) throws Exception {
		String result = importOlx();

		for (Listing l : listings.findAll()) {
			assertThat(String.join("|", String.valueOf(l.getTitle()), l.getUrl(), String.valueOf(l.getCity()),
					String.valueOf(l.getDistrict()), String.valueOf(l.getMake()), String.valueOf(l.getModel()),
					String.valueOf(l.getVersion()), String.valueOf(l.getDescription()), l.getExternalId()))
					.doesNotContain(carfinder.source.olx.OlxFeedParserTest.SENTINELS)
					.doesNotContain("FAKEOLXCOOKIE", "FAKEOLXTOKEN", "FAKEOLXAUTH");
		}
		assertThat(result).doesNotContain(carfinder.source.olx.OlxFeedParserTest.SENTINELS);
		assertThat(output.getAll()).doesNotContain(carfinder.source.olx.OlxFeedParserTest.SENTINELS)
				.doesNotContain("FAKEOLXCOOKIE", "FAKEOLXTOKEN", "FAKEOLXAUTH");
	}

	@Test
	void notOlxHar_400Pt() throws Exception {
		for (byte[] body : List.of("hello".getBytes(), Files.readAllBytes(FIXTURE))) {
			mvc.perform(post("/api/imports/olx").contentType(MediaType.APPLICATION_OCTET_STREAM).content(body))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.messagePt").value(OlxFeedParser.NO_ADS));
		}
	}
}
