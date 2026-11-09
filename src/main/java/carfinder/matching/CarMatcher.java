package carfinder.matching;

import carfinder.knowledge.Engine;
import carfinder.knowledge.EngineFault;
import carfinder.knowledge.GearboxRule;
import carfinder.knowledge.GearboxType;
import carfinder.knowledge.Generation;
import carfinder.knowledge.KnowledgeBase;
import carfinder.knowledge.Text;
import carfinder.knowledge.Tier;
import carfinder.listing.Listing;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** Matches a listing to a generation, engine, engine faults and risky gearboxes. Unknown values skip a check. */
@Component
public class CarMatcher {

	/** The app's own rule, not a cited source: "automático" on a pre-2012 small car is usually a robotised box. */
	static final String AUTOMATIC_SOURCE = "regra do car-finder: automático antes de 2012 costuma ser robotizado";
	private static final GearboxRule AUTOMATIC_RULE = new GearboxRule("autom", GearboxType.ROBOTISED, Tier.HIGH,
			AUTOMATIC_SOURCE, "I");
	private static final int EDGE_MONTHS = 6;
	/** "1.3", "1,4", "1.4tdci"; not "1.500" or "11.3". */
	private static final Pattern LITRES_HINT = Pattern.compile("(?<![\\d.,])(\\d)[.,](\\d)(?![\\d])");
	private static final Pattern LITRES_FAMILY = Pattern.compile("(\\d)\\.(\\d)");
	/** A word in the version or title that names one generation among several (the Clio II Campus was sold until 2012). */
	private static final Map<String, String> GENERATION_KEYWORDS = Map.of("campus", "clio-ii");

	private final KnowledgeBase kb;

	public CarMatcher(KnowledgeBase kb) {
		this.kb = kb;
	}

	public CarMatch match(Listing l) {
		boolean monthKnown = l.getMonth() != null && l.getMonth() >= 1 && l.getMonth() <= 12;
		YearMonth reg = l.getYear() == null ? null : YearMonth.of(l.getYear(), monthKnown ? l.getMonth() : 6);

		List<Generation> candidates = reg == null || l.getMake() == null || l.getModel() == null ? List.of()
				: kb.generationsFor(l.getMake(), l.getModel()).stream()
						.filter(g -> g.covers(reg)).toList();
		candidates = byKeyword(l, candidates);
		Optional<Generation> generation = candidates.size() == 1
				&& !candidates.getFirst().changeoverYears().contains(reg.getYear())
						? Optional.of(candidates.getFirst()) : Optional.empty();

		EngineChoice choice = engine(l, candidates);
		Optional<Engine> engine = choice.engine();
		List<FaultHit> faults = reg == null ? List.of()
				: engine.stream().flatMap(e -> kb.faultsFor(e.id()).stream())
						.map(f -> hit(f, reg, monthKnown)).flatMap(Optional::stream).toList();

		return new CarMatch(generation, candidates, generation.isEmpty() && !candidates.isEmpty(), engine, faults,
				gearbox(l, candidates), faults.stream().anyMatch(FaultHit::uncertain), choice.options());
	}

	private static List<Generation> byKeyword(Listing l, List<Generation> candidates) {
		String text = Text.normalise(Stream.of(l.getVersion(), l.getTitle()).filter(t -> t != null)
				.collect(Collectors.joining(" ")));
		List<Generation> named = candidates.stream().filter(g -> GENERATION_KEYWORDS.entrySet().stream()
				.anyMatch(k -> k.getValue().equals(g.id()) && wordIn(k.getKey(), text))).toList();
		return named.isEmpty() ? candidates : named;
	}

	private static boolean wordIn(String word, String normalisedText) {
		return Pattern.compile("(?<![\\p{Alnum}])" + Pattern.quote(Text.normalise(word)) + "(?![\\p{Alnum}])")
				.matcher(normalisedText).find();
	}

	private record EngineChoice(Optional<Engine> engine, int options) {
	}

	private EngineChoice engine(Listing l, List<Generation> candidates) {
		Stream<Engine> pool;
		if (!candidates.isEmpty()) {
			pool = candidates.stream().flatMap(g -> kb.enginesFor(g.id()).stream()).distinct();
		} else if (l.getMake() != null) {
			pool = kb.enginesForMake(l.getMake()).stream();
		} else {
			return new EngineChoice(Optional.empty(), 0);
		}
		Integer kw = l.getPowerCv() == null ? null : (int) Math.round(l.getPowerCv() / 1.36);
		List<Engine> kept = pool
				.filter(e -> e.fuel() == null || l.getFuel() == null || e.fuel().equals(l.getFuel().name()))
				.filter(e -> e.cc() == null || l.getCc() == null || Math.abs(e.cc() - l.getCc()) <= 5)
				.filter(e -> e.kwList().isEmpty() || kw == null || e.kwList().stream().anyMatch(k -> Math.abs(k - kw) <= 2))
				.toList();
		// Without a cc, a displacement in the text ("1.3", "1.4tdci") narrows; engines with no known litres stay in.
		Set<String> hint = l.getCc() == null ? hintLitres(l) : Set.of();
		if (!hint.isEmpty()) {
			kept = kept.stream().filter(e -> litres(e).isEmpty() || litres(e).stream().anyMatch(hint::contains))
					.toList();
		}
		if (kept.size() == 1) {
			return new EngineChoice(Optional.of(kept.getFirst()), 1);
		}
		// Engines with unknown cc and kW pass every check; prefer the one survivor matched on a known value.
		List<Engine> onKnown = kept.stream()
				.filter(e -> (e.cc() != null && l.getCc() != null) || (!e.kwList().isEmpty() && kw != null)
						|| litres(e).stream().anyMatch(hint::contains))
				.toList();
		return onKnown.size() == 1 ? new EngineChoice(Optional.of(onKnown.getFirst()), 1)
				: new EngineChoice(Optional.empty(), kept.size());
	}

	private static Set<String> hintLitres(Listing l) {
		Set<String> found = new HashSet<>();
		for (String t : new String[] {l.getVersion(), l.getTitle()}) {
			if (t != null) {
				Matcher m = LITRES_HINT.matcher(t);
				while (m.find()) {
					found.add(m.group(1) + "." + m.group(2));
				}
			}
		}
		return found;
	}

	/** From cc when known ("1298" -> 1.3), else every "d.d" in the sourced family name ("1.4/1.6 VVT-i" -> 1.4, 1.6). */
	private static Set<String> litres(Engine e) {
		if (e.cc() != null) {
			return Set.of(String.format(Locale.ROOT, "%.1f", Math.round(e.cc() / 100.0) / 10.0));
		}
		Set<String> found = new HashSet<>();
		Matcher m = LITRES_FAMILY.matcher(e.family() == null ? "" : e.family());
		while (m.find()) {
			found.add(m.group(1) + "." + m.group(2));
		}
		return found;
	}

	/** With an unknown month the fault hits (uncertain) if its window overlaps any month of the year. */
	private static Optional<FaultHit> hit(EngineFault f, YearMonth reg, boolean monthKnown) {
		YearMonth from = f.buildFrom();
		YearMonth to = f.buildTo();
		YearMonth earliest = monthKnown ? reg : reg.withMonth(1);
		YearMonth latest = monthKnown ? reg : reg.withMonth(12);
		if ((from != null && latest.isBefore(from)) || (to != null && earliest.isAfter(to.plusMonths(EDGE_MONTHS)))) {
			return Optional.empty();
		}
		boolean nearEdge = (from != null && Math.abs(ChronoUnit.MONTHS.between(from, reg)) <= EDGE_MONTHS)
				|| (to != null && Math.abs(ChronoUnit.MONTHS.between(to, reg)) <= EDGE_MONTHS);
		return Optional.of(new FaultHit(f, nearEdge || !monthKnown));
	}

	private List<GearboxHit> gearbox(Listing l, List<Generation> candidates) {
		List<String> texts = Stream.of(l.getGearbox(), l.getVersion(), l.getTitle())
				.filter(t -> t != null && !t.isBlank()).toList();
		List<GearboxHit> hits = new ArrayList<>();
		for (GearboxRule rule : kb.gearboxRules()) {
			texts.stream().filter(t -> wordIn(rule.keyword(), Text.normalise(t))).findFirst()
					.ifPresent(t -> hits.add(new GearboxHit(rule, t)));
		}
		boolean smallOldAutomatic = hits.isEmpty() && l.getGearbox() != null && l.getYear() != null
				&& l.getYear() < 2012 && !candidates.isEmpty()
				&& candidates.stream().allMatch(g -> Text.normalise(g.segment()).equals("b"))
				&& Text.normalise(l.getGearbox()).contains("autom");
		if (smallOldAutomatic) {
			hits.add(new GearboxHit(AUTOMATIC_RULE, l.getGearbox()));
		}
		return hits;
	}
}
