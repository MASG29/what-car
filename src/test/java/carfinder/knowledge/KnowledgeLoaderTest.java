package carfinder.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KnowledgeLoaderTest {

	private static final Path FIXTURES = Paths.get("src/test/resources/fixtures/knowledge");

	@TempDir
	Path tmp;

	private Path copyFixtures() throws IOException {
		try (var files = Files.list(FIXTURES)) {
			for (Path p : files.toList()) {
				Files.copy(p, tmp.resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING);
			}
		}
		return tmp;
	}

	@Test
	void loadsValidFixtureSet() {
		KnowledgeBase kb = KnowledgeLoader.load(FIXTURES);

		var polo = kb.generationsFor("Volkswagen", "Polo").getFirst();
		assertThat(polo.changeoverYears()).containsExactly(2013);
		assertThat(polo.prodEnd()).hasToString("2015-06");
		assertThat(kb.generationsFor("Renault", "Clio").getFirst().prodEnd()).isNull();
		assertThat(kb.surveysForGeneration("fake-polo-1")).hasSize(1);
		assertThat(kb.seriesSurveys("Renault", "Clio")).extracting(SurveyResult::cause).containsExactly(Cause.BATTERY);
		assertThat(kb.enginesFor("fake-polo-1")).extracting(Engine::kwList).containsExactly(java.util.List.of(55, 44));
		assertThat(kb.enginesForMake("vw")).extracting(Engine::id).containsExactly("fake-eng-1");
		assertThat(kb.faultsFor("fake-eng-1")).extracting(EngineFault::fault).containsExactly("Fake fault, with comma");
		assertThat(kb.gearboxRules()).hasSize(1);
		assertThat(kb.consumption("fake-polo-1", "fake-eng-1")).isPresent();
		assertThat(kb.consumptionInSegment("b", Cycle.NEDC)).hasSize(1);
		assertThat(kb.consumptionInSegment("B", Cycle.WLTP)).isEmpty();
	}

	@Test
	void missingSourceUrl_failsWithFileAndLine() throws IOException {
		Path dir = copyFixtures();
		Files.writeString(dir.resolve("consumption.csv"), String.join("\n",
				"generation_id,engine_id,l_per_100km,cycle,source_url,tag",
				"fake-polo-1,fake-eng-1,5.5,NEDC,https://example.org/cons,Q",
				"fake-polo-1,fake-eng-1,5.6,WLTP,,Q", ""));

		assertThatThrownBy(() -> KnowledgeLoader.load(dir)).isInstanceOf(IllegalStateException.class)
				.hasMessageStartingWith("consumption.csv:3: ");
	}

	@Test
	void unknownEngineIdInFitment_fails() throws IOException {
		Path dir = copyFixtures();
		Files.writeString(dir.resolve("engine_fitment.csv"),
				"generation_id,engine_id\nfake-polo-1,nope\n");

		assertThatThrownBy(() -> KnowledgeLoader.load(dir)).isInstanceOf(IllegalStateException.class)
				.hasMessageStartingWith("engine_fitment.csv:2: ").hasMessageContaining("nope");
	}

	@Test
	void badEnum_fails() throws IOException {
		Path dir = copyFixtures();
		Files.writeString(dir.resolve("gearbox_rules.csv"),
				"keyword,type,tier,source_url,tag\nfakebox,WRONG,HIGH,https://example.org/g,F\n");

		assertThatThrownBy(() -> KnowledgeLoader.load(dir))
				.hasMessageStartingWith("gearbox_rules.csv:2: ").hasMessageContaining("WRONG");
	}

	@Test
	void wrongHeader_fails() throws IOException {
		Path dir = copyFixtures();
		Files.writeString(dir.resolve("engines.csv"), "engine_id,make\n");

		assertThatThrownBy(() -> KnowledgeLoader.load(dir)).hasMessageStartingWith("engines.csv:1: ");
	}

	@Test
	void emptyKnowledgeBaseOnClasspath_loads() {
		assertThat(KnowledgeLoader.loadClasspath().gearboxRules()).isEmpty();
	}

	@Test
	void normalise_stripsAccentsAndCase() {
		assertThat(Text.normalise("Citroën Mégane ")).isEqualTo("citroen megane");
	}

	@Test
	void generationsFor_matchesAlias() {
		KnowledgeBase kb = KnowledgeLoader.load(FIXTURES);

		assertThat(kb.generationsFor("VW", "Polo")).extracting(Generation::id).containsExactly("fake-polo-1");
		assertThat(kb.generationsFor("VW", "Clio")).isEmpty();
	}
}
