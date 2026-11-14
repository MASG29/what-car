package carfinder.knowledge;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

	/**
	 * Finds the make and model in free text (an ad title) and returns a generation carrying the canonical names. The
	 * make (or a make alias such as "vw") and the model must both appear as whole words, or a model alias such as
	 * "mazda2" alone. A model named by a number ("206") counts only right after the make. The longest model wins
	 * ("Grande Punto" over "Punto"), then the earliest in the text.
	 */
	public Optional<Generation> recogniseMakeModel(String text) {
		String t = " " + Text.normalise(text).replaceAll("[^a-z0-9]+", " ") + " ";
		Generation best = null;
		int bestPos = 0;
		for (Generation g : generations) {
			String make = word(g.make());
			String model = word(g.model());
			List<String> makeWords = Stream.concat(Stream.of(make),
					g.aliases().stream().map(KnowledgeBase::word).filter(a -> !a.contains(model.strip()))).toList();
			int pos;
			if (model.strip().matches("\\d+")) {
				// "Peugeot 407 2.0 HDi 206.000 km" is no 206: a number counts only right after the make.
				pos = makeWords.stream().mapToInt(m -> {
					int i = t.indexOf(m.stripTrailing() + model);
					return i < 0 ? -1 : i + m.stripTrailing().length();
				}).filter(i -> i >= 0).min().orElse(-1);
			} else {
				pos = makeWords.stream().anyMatch(t::contains) ? t.indexOf(model) : -1;
			}
			if (pos < 0) {
				pos = g.aliases().stream().map(KnowledgeBase::word).filter(a -> a.contains(model.strip()))
						.mapToInt(t::indexOf).filter(i -> i >= 0).min().orElse(-1);
			}
			if (pos >= 0 && (best == null || g.model().length() > best.model().length()
					|| g.model().length() == best.model().length() && pos < bestPos)) {
				best = g;
				bestPos = pos;
			}
		}
		return best != null ? Optional.of(best) : namedGeneration(text);
	}

	private static final List<String> ROMAN = List.of("i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x");

	/**
	 * A model followed by one of its generation codes ("Golf mk4", "Golf IV", "Clio III"), even without the make. Only
	 * roman codes and Mk codes count: a bare digit ("Golf 4 portas") or a letter code ("Mazda 2 DE") never does. "mkN"
	 * reads as roman N only on rows coded in roman numerals; Mk-coded rows (Fiesta) match only as written, because
	 * their numbering differs by market. Empty unless exactly one generation matches.
	 */
	public Optional<Generation> namedGeneration(String text) {
		String t = word(text);
		List<Generation> hits = generations.stream()
				.filter(g -> codeWords(g.code()).stream().anyMatch(c -> t.contains(word(g.model()) + c + " ")))
				.toList();
		return hits.size() == 1 ? Optional.of(hits.getFirst()) : Optional.empty();
	}

	/** How a title may write the code: "iv", "mk4", "mk 4" for IV; "mk6", "mk 6" for Mk6; nothing for other codes. */
	private static Set<String> codeWords(String code) {
		String first = code == null || code.isBlank() ? "" : Text.normalise(code).strip().split("\\s+")[0];
		int roman = ROMAN.indexOf(first) + 1;
		if (roman > 0) {
			return Set.of(first, "mk" + roman, "mk " + roman);
		}
		return first.matches("mk\\d+") ? Set.of(first, "mk " + first.substring(2)) : Set.of();
	}

	/**
	 * What the user typed as "make model": as in a title, or a model alone that only one make has ("fiesta"). A bare
	 * digit ("2") is never a model on its own.
	 */
	public Optional<Generation> recogniseTyped(String text) {
		Optional<Generation> g = recogniseMakeModel(text);
		if (g.isPresent()) {
			return g;
		}
		String t = word(text);
		if (t.strip().matches("\\d")) {
			return Optional.empty();
		}
		List<Generation> same = generations.stream().filter(x -> word(x.model()).equals(t)).toList();
		return same.stream().map(Generation::make).distinct().count() == 1 ? Optional.of(same.get(0)) : Optional.empty();
	}

	/**
	 * The known "Make Model" closest to a typo ("ford fiesa" → "Ford Fiesta"), matched against "make model" and the
	 * model alone. At most 1 edit for short names, 2 from 8 characters, so unrelated words get no suggestion.
	 */
	public Optional<String> closestMakeModel(String text) {
		String t = word(text).strip();
		String best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (Generation g : generations) {
			for (String candidate : List.of(word(g.make() + " " + g.model()).strip(), word(g.model()).strip())) {
				int d = levenshtein(t, candidate);
				if (d <= (candidate.length() >= 8 ? 2 : 1) && candidate.length() > 2 && d < bestDistance) {
					best = g.make() + " " + g.model();
					bestDistance = d;
				}
			}
		}
		return Optional.ofNullable(best);
	}

	private static int levenshtein(String a, String b) {
		int[] prev = new int[b.length() + 1];
		for (int j = 0; j <= b.length(); j++) {
			prev[j] = j;
		}
		for (int i = 1; i <= a.length(); i++) {
			int[] cur = new int[b.length() + 1];
			cur[0] = i;
			for (int j = 1; j <= b.length(); j++) {
				int sub = prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
				cur[j] = Math.min(sub, Math.min(prev[j] + 1, cur[j - 1] + 1));
			}
			prev = cur;
		}
		return prev[b.length()];
	}

	private static String word(String s) {
		return " " + Text.normalise(s).replaceAll("[^a-z0-9]+", " ").strip() + " ";
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
