package carfinder.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks the real knowledge CSVs shipped in src/main/resources/knowledge. */
class KnowledgeDataTest {

	private static final KnowledgeBase KB = KnowledgeLoader.loadClasspath();

	/** id, make, model as Standvirtual names them. */
	private static final List<List<String>> SHORTLIST = List.of(
			List.of("colt-z30", "Mitsubishi", "Colt"),
			List.of("jazz-gd", "Honda", "Jazz"),
			List.of("jazz-ge", "Honda", "Jazz"),
			List.of("yaris-xp10", "Toyota", "Yaris"),
			List.of("yaris-xp90", "Toyota", "Yaris"),
			List.of("mazda2-dy", "Mazda", "2"),
			List.of("mazda2-de", "Mazda", "2"),
			List.of("fiesta-mk6", "Ford", "Fiesta"),
			List.of("fusion", "Ford", "Fusion"),
			List.of("polo-9n", "VW", "Polo"),
			List.of("golf-v", "VW", "Golf"),
			List.of("corolla-e120", "Toyota", "Corolla"),
			List.of("auris-e150", "Toyota", "Auris"),
			List.of("civic-7", "Honda", "Civic"),
			List.of("civic-8", "Honda", "Civic"),
			List.of("mazda3-bk", "Mazda", "3"));

	@Test
	void realDataLoads() {
		assertThat(KB.generationsFor("Renault", "Clio")).isNotEmpty();
		assertThat(KB.gearboxRules()).isNotEmpty();
	}

	@Test
	void everyShortlistGenerationHasAtLeastOneSurveyOrSeriesRow() {
		for (List<String> g : SHORTLIST) {
			String id = g.get(0), make = g.get(1), model = g.get(2);
			assertThat(KB.generationsFor(make, model)).as(id).extracting(Generation::id).contains(id);
			assertThat(KB.surveysForGeneration(id).size() + KB.seriesSurveys(make, model).size())
					.as(id).isPositive();
		}
	}

	@Test
	void clioIIHasAdacEz2004Row() {
		assertThat(KB.seriesSurveys("Renault", "Clio"))
				.filteredOn(s -> s.survey() == Survey.ADAC && Integer.valueOf(2004).equals(s.regYear()))
				.singleElement()
				.satisfies(s -> {
					assertThat(s.value()).isEqualTo(66.7);
					assertThat(s.tag()).isEqualTo("P");
					assertThat(s.sourceUrl()).startsWith("https://www.adac.de/");
				});
	}

	@Test
	void noConsumptionRowWithoutCycle() throws IOException {
		var lines = Files.readAllLines(Paths.get("src/main/resources/knowledge/consumption.csv"));
		for (String line : lines.subList(1, lines.size())) {
			assertThat(line.split(",", -1)[3]).as(line).isIn("NEDC", "WLTP");
		}
		for (String segment : List.of("B", "C")) {
			for (Cycle cycle : Cycle.values()) {
				assertThat(KB.consumptionInSegment(segment, cycle)).allSatisfy(c -> {
					assertThat(c.cycle()).isEqualTo(cycle);
					assertThat(c.sourceUrl()).startsWith("http");
				});
			}
		}
	}
}
