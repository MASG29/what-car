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

	private static List<String> recognised(String title) {
		return KB.recogniseMakeModel(title).map(g -> List.of(g.make(), g.model())).orElse(null);
	}

	@Test
	void recogniseMakeModel_returnsCanonicalNames() {
		assertThat(recognised("2006 VW Polo 1.2")).containsExactly("Volkswagen", "Polo");
		assertThat(recognised("Citroen C3 1.1 de 2005")).containsExactly("Citroën", "C3");
		assertThat(recognised("RENAULT MEGANE 1.5 dCi")).containsExactly("Renault", "Mégane");
		assertThat(recognised("2009 Fiat Grande Punto 1.2")).containsExactly("Fiat", "Grande Punto");
		assertThat(recognised("Fiat Punto 1.2 2004")).containsExactly("Fiat", "Punto");
		assertThat(recognised("Mazda2 1.4 2008")).containsExactly("Mazda", "2");
		assertThat(recognised("Mazda 3 1.6 2 donos")).containsExactly("Mazda", "3");
		assertThat(recognised("Peugeot 206 1.1")).containsExactly("Peugeot", "206");
	}

	@Test
	void recogniseMakeModel_noMakeOrUnknown_empty() {
		assertThat(recognised("Polo 1.2 bom estado")).isNull(); // model alone is not enough
		assertThat(recognised("2007 Opel Corsa 1.2")).isNull();
		assertThat(recognised("Peugeot 2060")).isNull(); // whole words only
		assertThat(recognised("")).isNull();
	}

	@Test
	void recogniseTyped_modelAloneOrMakeModel() {
		assertThat(KB.recogniseTyped("fiesta").map(g -> g.make() + " " + g.model())).hasValue("Ford Fiesta");
		assertThat(KB.recogniseTyped("  ford   FIESTA ").map(g -> g.make() + " " + g.model())).hasValue("Ford Fiesta");
		assertThat(KB.recogniseTyped("vw polo").map(g -> g.make() + " " + g.model())).hasValue("Volkswagen Polo");
		assertThat(KB.recogniseTyped("c3").map(g -> g.make() + " " + g.model())).hasValue("Citroën C3");
		assertThat(KB.recogniseTyped("toyota")).isEmpty();
		assertThat(KB.recogniseTyped("2")).isEmpty(); // a bare digit is not a model
	}

	@Test
	void closestMakeModel_suggestsForTypos() {
		assertThat(KB.closestMakeModel("ford fiesa")).hasValue("Ford Fiesta");
		assertThat(KB.closestMakeModel("fiessta")).hasValue("Ford Fiesta");
		assertThat(KB.closestMakeModel("toyota yaros")).hasValue("Toyota Yaris");
		assertThat(KB.closestMakeModel("abc")).isEmpty();
		assertThat(KB.closestMakeModel("opel corsa")).isEmpty();
	}

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

	@Test
	void consumption_sourcedAdemeRowsPinned() {
		assertThat(KB.consumption("yaris-xp90", "toyota-1kr")).hasValueSatisfying(c -> {
			assertThat(c.lPer100km()).isEqualTo(5.4);
			assertThat(c.cycle()).isEqualTo(Cycle.NEDC);
			assertThat(c.sourceUrl())
					.isEqualTo("https://www.data.gouv.fr/storage/f/2014-07-07T09-57-45/carlab-mars-2006-complete.zip");
			assertThat(c.tag()).isEqualTo("P");
		});
		assertThat(KB.consumption("peugeot-206", "psa-tu3")).map(ConsumptionFigure::lPer100km).hasValue(6.3);
		assertThat(KB.consumption("grande-punto", "fiat-1.3-multijet")).map(ConsumptionFigure::lPer100km)
				.hasValue(4.7);
		assertThat(KB.consumption("yaris-xp10", "toyota-xp10-1.0")).map(ConsumptionFigure::lPer100km)
				.hasValue(5.6); // brochure row kept; ADEME 2003 agrees
		assertThat(KB.consumption("fusion", "ford-sigma-1.25")).isEmpty(); // no matching official version
	}

	@Test
	void neighbourGenerations_sourcedWindows() {
		assertThat(KB.generationsFor("VW", "Golf")).extracting(Generation::id)
				.containsExactly("golf-iv", "golf-v", "golf-vi");
		assertThat(KB.generationsFor("VW", "Golf Variant")).extracting(Generation::id)
				.containsExactly("golf-iv-variant", "golf-v-variant", "golf-vi-variant");
		assertThat(KB.generationsFor("Fiat", "Punto")).extracting(Generation::id)
				.containsExactly("punto-188", "punto-evo");
		assertThat(KB.generationsFor("Fiat", "Punto Evo")).extracting(Generation::id).containsExactly("punto-evo");
		var c3ii = KB.generationsFor("Citroën", "C3").getLast();
		assertThat(c3ii.id()).isEqualTo("c3-ii");
		assertThat(c3ii.prodStart()).isEqualTo(java.time.YearMonth.of(2009, 1));
		assertThat(c3ii.changeoverYears()).containsExactly(2009);
		assertThat(KB.surveysForGeneration("c3-ii")).isEmpty(); // no score rows without a source
	}

	@Test
	void psaPetrolEngines_kwFromAdeme() {
		assertThat(KB.enginesFor("peugeot-206")).filteredOn(e -> e.id().equals("psa-tu3")).singleElement()
				.extracting(Engine::kwList).isEqualTo(List.of(54, 55));
	}
}
