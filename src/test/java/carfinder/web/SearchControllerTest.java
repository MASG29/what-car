package carfinder.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import carfinder.buying.Checklist;
import carfinder.buying.RealCost;
import carfinder.listing.Fuel;
import carfinder.listing.Listing;
import carfinder.listing.RawListing;
import carfinder.listing.Site;
import carfinder.listing.TestListings;
import carfinder.scoring.ConditionResult;
import carfinder.scoring.Criterion;
import carfinder.scoring.Excluded;
import carfinder.scoring.ScoredListing;
import carfinder.scoring.ScoringService;
import carfinder.scoring.SubScore;
import carfinder.search.RunStatus;
import carfinder.search.RunView;
import carfinder.search.SearchService;
import carfinder.source.SourceException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.OptionalDouble;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({SearchController.class, ScoreLinkController.class})
class SearchControllerTest {

	private static final String LIMITS = """
			{"maxPriceEur": 2500, "maxKm": 300000, "minYear": 2000, "fuels": ["PETROL"]}""";
	private static final String AD = "https://www.standvirtual.com/carros/anuncio/renault-clio-IDabc123.html";

	@Autowired
	MockMvc mvc;

	@MockitoBean
	SearchService service;

	private static String search(String limits) {
		return "{\"limits\": " + limits + ", \"preset\": \"BALANCED\", \"sites\": [\"STANDVIRTUAL\"]}";
	}

	@Test
	void postSearch_valid_202WithId() throws Exception {
		UUID id = UUID.randomUUID();
		when(service.start(any(), any(), any(), any(), anyBoolean())).thenReturn(id);

		mvc.perform(post("/api/searches").contentType(MediaType.APPLICATION_JSON).content(search(LIMITS)))
				.andExpect(status().isAccepted()).andExpect(jsonPath("$.id").value(id.toString()));
	}

	@Test
	void unknownMakeModel_400WithSuggestion() throws Exception {
		when(service.start(any(), any(), any(), any(), anyBoolean()))
				.thenThrow(new carfinder.search.UnknownMakeModelException("Ford Fiesa", "Ford Fiesta"));

		mvc.perform(post("/api/searches").contentType(MediaType.APPLICATION_JSON).content(search(LIMITS)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.messagePt").value(org.hamcrest.Matchers.startsWith("Marca e modelo desconhecidos")))
				.andExpect(jsonPath("$.suggestion").value("Ford Fiesta"));
	}

	@Test
	void plainBadRequest_hasNoSuggestionField() throws Exception {
		when(service.start(any(), any(), any(), any(), anyBoolean())).thenThrow(new IllegalArgumentException("x"));

		mvc.perform(post("/api/searches").contentType(MediaType.APPLICATION_JSON).content(search(LIMITS)))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.suggestion").doesNotExist());
	}

	@Test
	void postSearch_marketplaceAutomated_passedOn() throws Exception {
		when(service.start(any(), any(), any(), any(), anyBoolean())).thenReturn(UUID.randomUUID());
		String body = "{\"limits\": " + LIMITS
				+ ", \"preset\": \"BALANCED\", \"sites\": [\"MARKETPLACE\"], \"marketplaceAutomated\": true}";

		mvc.perform(post("/api/searches").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isAccepted());
		verify(service).start(any(), any(), any(), any(), eq(true));
	}

	@Test
	void postSearch_minDoors_optionalAndPassedOn() throws Exception {
		when(service.start(any(), any(), any(), any(), anyBoolean())).thenReturn(UUID.randomUUID());
		String withDoors = "{\"maxPriceEur\": 2500, \"maxKm\": 300000, \"minYear\": 2000, \"fuels\": [\"PETROL\"], "
				+ "\"minDoors\": 5}";
		mvc.perform(post("/api/searches").contentType(MediaType.APPLICATION_JSON).content(search(withDoors)))
				.andExpect(status().isAccepted());
		verify(service).start(argThat(l -> Integer.valueOf(5).equals(l.minDoors())), any(), any(), any(), eq(false));
	}

	@Test
	void postSearch_invalidLimits_400() throws Exception {
		mvc.perform(post("/api/searches").contentType(MediaType.APPLICATION_JSON)
				.content(search("{\"maxPriceEur\": 2500, \"minYear\": 2000, \"fuels\": [\"PETROL\"]}")))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"{\"maxPriceEur\": 2500, \"maxKm\": 300000, \"minYear\": 2000, \"fuels\": [null]}",
			"{\"maxPriceEur\": 2500, \"maxKm\": 300000, \"minYear\": 2000, \"fuels\": [\"PETROL\"], \"models\": [null]}",
			"{\"maxPriceEur\": 2500, \"maxKm\": 300000, \"minYear\": 2000, \"fuels\": []}",
			"{\"maxPriceEur\": 0, \"maxKm\": 300000, \"minYear\": 2000, \"fuels\": [\"PETROL\"]}",
			"{\"maxPriceEur\": 2500, \"maxKm\": 300000, \"minYear\": 2000, \"fuels\": [\"PETROL\"], \"minDoors\": 0}",
			"{\"maxPriceEur\": 2500, \"maxKm\": 300000, \"minYear\": 2000, \"fuels\": [\"PETROL\"], \"realCost\": "
					+ "{\"paperworkEur\": -1, \"ipoEur\": 37.47, \"iucPre2007Eur\": 40, \"iucPost2007Eur\": 149, "
					+ "\"beltEur\": 350, \"insuranceMinEur\": 800, \"insuranceMaxEur\": 1500}}"})
	void postSearch_badLimitValues_400(String limits) throws Exception {
		mvc.perform(post("/api/searches").contentType(MediaType.APPLICATION_JSON).content(search(limits)))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}

	@Test
	void postSearch_unknownCentre_400WithPtMessage() throws Exception {
		when(service.start(any(), any(), any(), any(), anyBoolean()))
				.thenThrow(new IllegalArgumentException("Localidade desconhecida: Atlantida"));
		String limits = "{\"maxPriceEur\": 2500, \"maxKm\": 300000, \"minYear\": 2000, \"fuels\": [\"PETROL\"], "
				+ "\"centre\": \"Atlantida\", \"radiusKm\": 65}";

		mvc.perform(post("/api/searches").contentType(MediaType.APPLICATION_JSON).content(search(limits)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.messagePt").value("Localidade desconhecida: Atlantida"));
	}

	@Test
	void postSearch_centreAndRadiusReachTheService() throws Exception {
		when(service.start(any(), any(), any(), any(), anyBoolean())).thenReturn(UUID.randomUUID());
		String limits = "{\"maxPriceEur\": 2500, \"maxKm\": 300000, \"minYear\": 2000, \"fuels\": [\"PETROL\"], "
				+ "\"centre\": \"Ponte de Lima\", \"radiusKm\": 65}";

		mvc.perform(post("/api/searches").contentType(MediaType.APPLICATION_JSON).content(search(limits)))
				.andExpect(status().isAccepted());
		verify(service).start(argThat(l -> "Ponte de Lima".equals(l.centre()) && Integer.valueOf(65).equals(l.radiusKm())),
				any(), any(), any(), eq(false));
	}

	@Test
	void postSearch_missingLimitsOrSites_400() throws Exception {
		mvc.perform(post("/api/searches").contentType(MediaType.APPLICATION_JSON)
				.content("{\"preset\": \"BALANCED\", \"sites\": [\"STANDVIRTUAL\"]}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/searches").contentType(MediaType.APPLICATION_JSON)
				.content("{\"limits\": " + LIMITS + ", \"preset\": \"BALANCED\", \"sites\": [null]}"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}

	@Test
	void postSearch_whileRunning_409() throws Exception {
		when(service.start(any(), any(), any(), any(), anyBoolean()))
				.thenThrow(new IllegalStateException("Já há uma pesquisa a decorrer"));

		mvc.perform(post("/api/searches").contentType(MediaType.APPLICATION_JSON).content(search(LIMITS)))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.messagePt").value("Já há uma pesquisa a decorrer"));
	}

	@Test
	void postSearch_unsupportedSite_400() throws Exception {
		when(service.start(any(), any(), any(), any(), anyBoolean()))
				.thenThrow(new IllegalArgumentException("Site ainda não suportado"));

		mvc.perform(post("/api/searches").contentType(MediaType.APPLICATION_JSON).content(search(LIMITS)))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.messagePt").value("Site ainda não suportado"));
	}

	@Test
	void getStatus_returnsView_and404WhenUnknown() throws Exception {
		UUID id = UUID.randomUUID();
		when(service.status(id)).thenReturn(new RunView(id, RunStatus.NEEDS_USER, "Faz login", 3));
		UUID unknown = UUID.randomUUID();
		when(service.status(unknown)).thenThrow(new NoSuchElementException());

		mvc.perform(get("/api/searches/" + id)).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("NEEDS_USER"))
				.andExpect(jsonPath("$.messagePt").value("Faz login"))
				.andExpect(jsonPath("$.collected").value(3));
		mvc.perform(get("/api/searches/" + unknown)).andExpect(status().isNotFound());
	}

	private static Listing ad(String id) {
		// sellerType, km and most detail fields are null on purpose
		return TestListings.of(new RawListing(Site.STANDVIRTUAL, id, "https://example.test/" + id, "Clio " + id, 2000,
				"Renault", "Clio", null, 2008, null, null, Fuel.PETROL, null, null, null, null, null, null, null, 0,
				null, null, false), false);
	}

	@Test
	void getResults_exposesRankedExcludedAndReasonCounts() throws Exception {
		UUID id = UUID.randomUUID();
		SubScore none = new SubScore(OptionalDouble.empty(), 0, "sem dados", List.of(), List.of());
		SubScore some = new SubScore(OptionalDouble.of(0.7), 0.8, "bom", List.of("https://example.test/s"),
				List.of("km não indicados"));
		ScoredListing kept = new ScoredListing(ad("k"), List.of(), Map.of(Criterion.PRICE, some,
				Criterion.RELIABILITY, none), 0.7, 0.5, 0.52, true, false, List.of("km não indicados"), List.of(),
				new RealCost(2500, Map.of("Preço pedido", 2000.0), "800 a 1500 €/ano"),
				new Checklist(List.of(new Checklist.Link("IMT", "https://example.test/imt")), List.of("Revisões?")),
				new ConditionResult(List.of(), List.of()), "1.º lugar: preço 70 %; confiança 50 %.");
		List<Excluded> excluded = List.of(
				new Excluded(ad("x1"), List.of("preço acima do teu limite (2600 €)", "ano abaixo do teu limite (1999)")),
				new Excluded(ad("x2"), List.of("preço acima do teu limite (2800 €)")));
		when(service.results(id)).thenReturn(new ScoringService.Ranking(List.of(kept), excluded));

		mvc.perform(get("/api/searches/" + id + "/results")).andExpect(status().isOk())
				.andExpect(jsonPath("$.ranked[0].listing.title").value("Clio k"))
				.andExpect(jsonPath("$.ranked[0].listing.sellerType").doesNotExist())
				.andExpect(jsonPath("$.ranked[0].listing.lastSeen").isString())
				.andExpect(jsonPath("$.ranked[0].subs.PRICE.value").value(0.7))
				.andExpect(jsonPath("$.ranked[0].subs.RELIABILITY.value").doesNotExist())
				.andExpect(jsonPath("$.ranked[0].subs.RELIABILITY.explanationPt").value("sem dados"))
				.andExpect(jsonPath("$.ranked[0].top5").value(true))
				.andExpect(jsonPath("$.ranked[0].whyPt").value("1.º lugar: preço 70 %; confiança 50 %."))
				.andExpect(jsonPath("$.ranked[0].realCost.totalEur").value(2500.0))
				.andExpect(jsonPath("$.ranked[0].checklist.links[0].url").value("https://example.test/imt"))
				.andExpect(jsonPath("$.excluded[1].reasonsPt[0]").value("preço acima do teu limite (2800 €)"))
				.andExpect(jsonPath("$.excludedByReason['preço acima do teu limite']").value(2))
				.andExpect(jsonPath("$.excludedByReason['ano abaixo do teu limite']").value(1));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"http://www.standvirtual.com/carros/anuncio/renault-clio-IDabc123.html",
			"https://www.standvirtual.com.evil.test/carros/anuncio/renault-clio-IDabc123.html",
			"https://standvirtual.com/carros/anuncio/renault-clio-IDabc123.html",
			"https://user@www.standvirtual.com/carros/anuncio/renault-clio-IDabc123.html",
			"https://www.standvirtual.com:8443/carros/anuncio/renault-clio-IDabc123.html",
			"https://www.standvirtual.com/api/carros/anuncio/x",
			"https://www.standvirtual.com/carros/anuncio/../../api/x",
			"https://www.standvirtual.com/carros/anuncio/%2e%2e/%2e%2e/ad/contact/x",
			"https://www.standvirtual.com/carros/anuncio/",
			"https://www.standvirtual.com/carros?search%5Bfilter_float_price%3Ato%5D=2500",
			"http://www.facebook.com/marketplace/item/123456789/",
			"https://facebook.com/marketplace/item/123456789/",
			"https://m.facebook.com/marketplace/item/123456789/",
			"https://www.facebook.com.evil.test/marketplace/item/123456789/",
			"https://user@www.facebook.com/marketplace/item/123456789/",
			"https://www.facebook.com:8443/marketplace/item/123456789/",
			"https://www.facebook.com/marketplace/item/abc/",
			"https://www.facebook.com/marketplace/item/123/../../api/graphql/",
			"https://www.facebook.com/marketplace/item/123/photos/",
			"https://www.facebook.com/api/graphql/",
			"https://www.facebook.com/marketplace/",
			"https://www.facebook.com/login/",
			"http://www.custojusto.pt/braga/veiculos/carros-usados/citadino/renault-clio-90000001",
			"https://custojusto.pt/braga/veiculos/carros-usados/citadino/renault-clio-90000001",
			"https://www.custojusto.pt.evil.test/braga/veiculos/carros-usados/citadino/renault-clio-90000001",
			"https://user@www.custojusto.pt/braga/veiculos/carros-usados/citadino/renault-clio-90000001",
			"https://www.custojusto.pt:8443/braga/veiculos/carros-usados/citadino/renault-clio-90000001",
			"https://www.custojusto.pt/braga/veiculos/carros-usados/citadino/../../../api/x-1",
			"https://www.custojusto.pt/braga/veiculos/carros-usados",
			"https://www.custojusto.pt/braga/veiculos/carros-usados/citadino",
			"https://www.custojusto.pt/braga/imoveis/apartamentos/t2-90000001",
			"https://www.custojusto.pt/v2/auth",
			"file:///etc/passwd",
			"não é um link",
			" "})
	void scoreLink_unsupportedSite_400(String url) throws Exception {
		mvc.perform(post("/api/score-link").contentType(MediaType.APPLICATION_JSON)
				.content("{\"url\": \"" + url + "\"}")).andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}

	@ParameterizedTest
	@ValueSource(strings = {"https://www.facebook.com/marketplace/item/123456789/",
			"https://www.facebook.com/marketplace/item/123456789",
			"https://WWW.facebook.com/marketplace/item/123456789/?ref=search&tracking=x#photo"})
	void scoreLink_acceptsMarketplaceItem_rejectsOtherPaths(String url) throws Exception {
		String clean = "https://www.facebook.com/marketplace/item/123456789/";
		when(service.scoreLink(eq(Site.MARKETPLACE), eq(clean), any(), any(), any()))
				.thenReturn(new ScoringService.Ranking(List.of(), List.of()));

		mvc.perform(post("/api/score-link").contentType(MediaType.APPLICATION_JSON)
				.content("{\"url\": \"" + url + "\"}")).andExpect(status().isOk());
		verify(service).scoreLink(eq(Site.MARKETPLACE), eq(clean), any(), any(), any());
	}

	@ParameterizedTest
	@ValueSource(strings = {"https://www.custojusto.pt/braga/veiculos/carros-usados/citadino/renault-clio-90000001",
			"https://WWW.custojusto.pt/braga/veiculos/carros-usados/citadino/renault-clio-90000001?utm_source=x#fotos"})
	void scoreLink_acceptsOnlyThisSitesAdUrls(String url) throws Exception {
		String clean = "https://www.custojusto.pt/braga/veiculos/carros-usados/citadino/renault-clio-90000001";
		when(service.scoreLink(eq(Site.CUSTOJUSTO), eq(clean), any(), any(), any()))
				.thenReturn(new ScoringService.Ranking(List.of(), List.of()));

		mvc.perform(post("/api/score-link").contentType(MediaType.APPLICATION_JSON)
				.content("{\"url\": \"" + url + "\"}")).andExpect(status().isOk());
		verify(service).scoreLink(eq(Site.CUSTOJUSTO), eq(clean), any(), any(), any());
	}

	@Test
	void scoreLink_standvirtualAd_scoresIt() throws Exception {
		when(service.scoreLink(eq(Site.STANDVIRTUAL), eq(AD), any(), any(), any()))
				.thenReturn(new ScoringService.Ranking(List.of(), List.of(new Excluded(ad("p"), List.of("anúncio de peças")))));

		mvc.perform(post("/api/score-link").contentType(MediaType.APPLICATION_JSON)
				.content("{\"url\": \"" + AD + "?utm_source=x#fotos\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.excludedByReason['anúncio de peças']").value(1));
		verify(service).scoreLink(eq(Site.STANDVIRTUAL), eq(AD), any(), any(), any());
	}

	@Test
	void scoreLink_removedAd_422WithMessage() throws Exception {
		when(service.scoreLink(any(), any(), any(), any(), any()))
				.thenThrow(new SourceException("Standvirtual respondeu com erro HTTP 404"));

		mvc.perform(post("/api/score-link").contentType(MediaType.APPLICATION_JSON).content("{\"url\": \"" + AD + "\"}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.messagePt").value("Standvirtual respondeu com erro HTTP 404"));
	}
}
