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
		mvc = MockMvcBuilders.standaloneSetup(new ImportController(parser, store, listings, search, data)).build();
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
		assertThat(handlers).hasSize(1);
		assertThat(handlers[0].getParameterTypes()).containsExactly(HttpServletRequest.class)
				.doesNotContain(MultipartFile.class);
	}
}
