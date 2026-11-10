package carfinder.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import carfinder.TestcontainersConfiguration;
import carfinder.data.DataStore;
import carfinder.listing.Listing;
import carfinder.listing.ListingRepository;
import carfinder.listing.ListingStore;
import carfinder.listing.RawListing;
import carfinder.listing.Site;
import carfinder.search.SearchService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import({TestcontainersConfiguration.class, ListingStore.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DataControllerTest {

	@Autowired
	ListingStore store;
	@Autowired
	ListingRepository listings;
	@Autowired
	JdbcTemplate jdbc;

	private final SearchService search = mock(SearchService.class);
	private DataStore data;
	private MockMvc mvc;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		clean();
		when(search.whileIdle(any())).thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(0)).get());
		data = new DataStore(jdbc, Clock.fixed(Instant.parse("2026-11-09T14:02:00Z"), ZoneOffset.UTC));
		mvc = MockMvcBuilders.standaloneSetup(new DataController(data, search)).build();
	}

	@AfterEach
	void clean() {
		// writes commit here; other test classes share the database
		jdbc.update("DELETE FROM search_run");
		jdbc.update("DELETE FROM import_batch");
		listings.deleteAll();
	}

	private Listing ad(Site site, String id) {
		return store.upsert(new RawListing(site, id, "https://example.org/" + id, "Renault Clio " + id, 2000,
				"Renault", "Clio", null, 2008, null, 150000, null, null, null, null, null, null, null, "Braga", 0,
				null, null, false));
	}

	private UUID runHolding(Listing l) {
		UUID id = UUID.randomUUID();
		jdbc.update("INSERT INTO search_run (id, limits_json, preset, status, started_at) VALUES (?, '{}', "
				+ "'BALANCED', 'DONE', now())", id);
		jdbc.update("INSERT INTO search_run_listing (run_id, listing_id) VALUES (?, ?)", id, l.getId());
		return id;
	}

	@Test
	void recordImport_listsNewestFirstWithCounts() throws Exception {
		Listing a = ad(Site.MARKETPLACE, "a");
		Listing b = ad(Site.MARKETPLACE, "b");
		long first = data.recordImport(Site.MARKETPLACE, 3, 2, List.of(a.getId(), b.getId()));
		long second = data.recordImport(Site.MARKETPLACE, 1, 0, List.of(a.getId()));

		mvc.perform(get("/api/imports")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(second)).andExpect(jsonPath("$[0].listings").value(1))
				.andExpect(jsonPath("$[1].id").value(first)).andExpect(jsonPath("$[1].read").value(3))
				.andExpect(jsonPath("$[1].added").value(2)).andExpect(jsonPath("$[1].listings").value(2))
				.andExpect(jsonPath("$[1].site").value("MARKETPLACE"))
				.andExpect(jsonPath("$[1].importedAt").value("2026-11-09T14:02:00Z"));
	}

	@Test
	void deleteImport_keepsAdsHeldByAnotherImport() throws Exception {
		Listing shared = ad(Site.MARKETPLACE, "shared");
		Listing only = ad(Site.MARKETPLACE, "only");
		ad(Site.STANDVIRTUAL, "sv");
		long first = data.recordImport(Site.MARKETPLACE, 2, 2, List.of(shared.getId(), only.getId()));
		data.recordImport(Site.MARKETPLACE, 1, 0, List.of(shared.getId()));

		mvc.perform(delete("/api/imports/" + first)).andExpect(status().isOk())
				.andExpect(jsonPath("$.deleted").value(1));

		assertThat(listings.findAll()).extracting(Listing::getExternalId).containsExactlyInAnyOrder("shared", "sv");
		assertThat(data.imports()).hasSize(1);
	}

	@Test
	void deleteImport_adInSavedRun() throws Exception {
		Listing kept = ad(Site.STANDVIRTUAL, "kept");
		Listing gone = ad(Site.MARKETPLACE, "gone");
		long batch = data.recordImport(Site.MARKETPLACE, 1, 1, List.of(gone.getId()));
		UUID run = runHolding(gone);
		jdbc.update("INSERT INTO search_run_listing (run_id, listing_id) VALUES (?, ?)", run, kept.getId());

		mvc.perform(delete("/api/imports/" + batch)).andExpect(status().isOk());

		assertThat(jdbc.queryForList("SELECT listing_id FROM search_run_listing WHERE run_id = ?", Long.class, run))
				.containsExactly(kept.getId());
	}

	@Test
	void deleteImport_unknown_404() throws Exception {
		mvc.perform(delete("/api/imports/999999")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.messagePt").value("Importação não encontrada"));
	}

	@Test
	void deleteSearches_removesRunsKeepsListings() throws Exception {
		Listing l = ad(Site.STANDVIRTUAL, "x");
		runHolding(l);
		runHolding(l);

		mvc.perform(delete("/api/searches")).andExpect(status().isOk()).andExpect(jsonPath("$.deleted").value(2));

		assertThat(jdbc.queryForObject("SELECT count(*) FROM search_run", Integer.class)).isZero();
		assertThat(listings.count()).isEqualTo(1);
	}

	@Test
	void deleteAll_emptiesEverything() throws Exception {
		Listing l = ad(Site.MARKETPLACE, "y");
		ad(Site.STANDVIRTUAL, "z");
		data.recordImport(Site.MARKETPLACE, 1, 1, List.of(l.getId()));
		runHolding(l);

		mvc.perform(delete("/api/data")).andExpect(status().isOk()).andExpect(jsonPath("$.deleted").value(2));

		assertThat(listings.count()).isZero();
		assertThat(data.imports()).isEmpty();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM search_run", Integer.class)).isZero();
	}

	@Test
	void deletesWhileRunning_409() throws Exception {
		ad(Site.STANDVIRTUAL, "busy");
		org.mockito.Mockito.doThrow(new IllegalStateException("Já há uma pesquisa a decorrer")).when(search).whileIdle(any());

		mvc.perform(delete("/api/data")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.messagePt").value("Já há uma pesquisa a decorrer"));
		mvc.perform(delete("/api/searches")).andExpect(status().isConflict());

		assertThat(listings.count()).isEqualTo(1);
	}
}
