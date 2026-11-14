package carfinder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CarFinderApplicationTests {

	@Autowired
	MockMvc mvc;

	@Test
	void contextLoads() {
	}

	@Test
	void openApiDocsListTheSearchEndpoints() throws Exception {
		mvc.perform(get("/v3/api-docs").header("Host", "localhost:8090")).andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/searches']").exists())
				.andExpect(jsonPath("$.paths['/api/score-link']").exists());
		mvc.perform(get("/swagger-ui.html").header("Host", "localhost:8090")).andExpect(status().is3xxRedirection());
	}

	@Test
	void api_onlyForLocalHostNames() throws Exception {
		// DNS rebinding: another name pointing at 127.0.0.1 must not reach the API
		mvc.perform(get("/api/imports").header("Host", "evil.example:8090")).andExpect(status().isForbidden());
		mvc.perform(get("/api/imports").header("Host", "127.0.0.1.evil.example")).andExpect(status().isForbidden());
		mvc.perform(get("/api/imports").header("Host", "localhost:8090")).andExpect(status().isOk());
		mvc.perform(get("/api/imports").header("Host", "127.0.0.1:8090")).andExpect(status().isOk());
		// paths that still reach /api after decoding
		for (String path : new String[] {"/%61pi/imports", "/api;x/imports", "//api/imports"}) {
			mvc.perform(get(java.net.URI.create(path)).header("Host", "evil.example:8090"))
					.andExpect(status().isForbidden());
		}
	}
}
