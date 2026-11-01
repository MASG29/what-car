package carfinder.knowledge;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Immutable view of the knowledge CSVs. Names are compared normalised; make aliases come from generations. */
public final class KnowledgeBase {

	private final List<Generation> generations;
	private final Map<String, Generation> generationById;
	private final Map<String, Engine> engineById;
	private final Map<String, List<String>> engineIdsByGeneration;
	private final List<SurveyResult> surveys;
	private final List<EngineFault> faults;
	private final List<GearboxRule> gearboxRules;
	private final List<ConsumptionFigure> consumption;

	KnowledgeBase(List<Generation> generations, List<SurveyResult> surveys, List<Engine> engines,
			Map<String, List<String>> engineIdsByGeneration, List<EngineFault> faults,
			List<GearboxRule> gearboxRules, List<ConsumptionFigure> consumption) {
		this.generations = List.copyOf(generations);
		this.generationById = generations.stream().collect(Collectors.toUnmodifiableMap(Generation::id, g -> g));
		this.engineById = engines.stream().collect(Collectors.toUnmodifiableMap(Engine::id, e -> e));
		this.engineIdsByGeneration = Map.copyOf(engineIdsByGeneration);
		this.surveys = List.copyOf(surveys);
		this.faults = List.copyOf(faults);
		this.gearboxRules = List.copyOf(gearboxRules);
		this.consumption = List.copyOf(consumption);
	}

	public List<Generation> generationsFor(String make, String model) {
		String m = Text.normalise(make);
		String mo = Text.normalise(model);
		return generations.stream().filter(g -> matches(g, m) && matches(g, mo)).toList();
	}

	public List<SurveyResult> surveysForGeneration(String id) {
		return filter(surveys, s -> id.equals(s.generationId()));
	}

	public List<SurveyResult> seriesSurveys(String make, String model) {
		String m = canonicalMake(make);
		String mo = Text.normalise(model);
		return filter(surveys, s -> s.generationId() == null && Text.normalise(s.make()).equals(m)
				&& Text.normalise(s.model()).equals(mo));
	}

	public List<Engine> enginesFor(String generationId) {
		return engineIdsByGeneration.getOrDefault(generationId, List.of()).stream().map(engineById::get).toList();
	}

	public List<Engine> enginesForMake(String make) {
		String m = canonicalMake(make);
		return engineById.values().stream().filter(e -> Text.normalise(e.make()).equals(m))
				.sorted(java.util.Comparator.comparing(Engine::id)).toList();
	}

	public List<EngineFault> faultsFor(String engineId) {
		return filter(faults, f -> f.engineId().equals(engineId));
	}

	public List<GearboxRule> gearboxRules() {
		return gearboxRules;
	}

	public Optional<ConsumptionFigure> consumption(String generationId, String engineId) {
		return consumption.stream()
				.filter(c -> c.generationId().equals(generationId) && c.engineId().equals(engineId)).findFirst();
	}

	public List<ConsumptionFigure> consumptionInSegment(String segment, Cycle cycle) {
		String s = Text.normalise(segment);
		return filter(consumption, c -> c.cycle() == cycle
				&& Text.normalise(generationById.get(c.generationId()).segment()).equals(s));
	}

	private static boolean matches(Generation g, String name) {
		return Text.normalise(g.make()).equals(name) || Text.normalise(g.model()).equals(name)
				|| g.aliases().stream().anyMatch(a -> Text.normalise(a).equals(name));
	}

	/** Maps a make alias (for example "vw") to the make name used in the CSVs. */
	private String canonicalMake(String make) {
		String m = Text.normalise(make);
		return generations.stream()
				.filter(g -> g.aliases().stream().anyMatch(a -> Text.normalise(a).equals(m)))
				.map(g -> Text.normalise(g.make())).findFirst().orElse(m);
	}

	private static <T> List<T> filter(List<T> list, Predicate<T> p) {
		return list.stream().filter(p).toList();
	}
}
