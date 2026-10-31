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
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** Matches a listing to a generation, engine, engine faults and risky gearboxes. Unknown values skip a check. */
@Component
public class CarMatcher {

	/** "automático" on a pre-2012 small car is usually a robotised single-clutch box. */
	static final String AUTOMATIC_SOURCE = "reports/Fiabilidade carros segmentos B e C.md";
	private static final GearboxRule AUTOMATIC_RULE = new GearboxRule("autom", GearboxType.ROBOTISED, Tier.HIGH,
			AUTOMATIC_SOURCE, "I");
	private static final int EDGE_MONTHS = 6;

	private final KnowledgeBase kb;

	public CarMatcher(KnowledgeBase kb) {
		this.kb = kb;
	}

	public CarMatch match(Listing l) {
		boolean monthKnown = l.getMonth() != null && l.getMonth() >= 1 && l.getMonth() <= 12;
		YearMonth reg = l.getYear() == null ? null : YearMonth.of(l.getYear(), monthKnown ? l.getMonth() : 6);

		List<Generation> candidates = reg == null || l.getMake() == null || l.getModel() == null ? List.of()
				: kb.generationsFor(l.getMake(), l.getModel()).stream()
						.filter(g -> !reg.isBefore(g.prodStart())
								&& (g.prodEnd() == null || !reg.isAfter(g.prodEnd().plusMonths(EDGE_MONTHS))))
						.toList();
		Optional<Generation> generation = candidates.size() == 1
				&& !candidates.getFirst().changeoverYears().contains(reg.getYear())
						? Optional.of(candidates.getFirst()) : Optional.empty();

		Optional<Engine> engine = engine(l, candidates);
		List<FaultHit> faults = reg == null ? List.of()
				: engine.stream().flatMap(e -> kb.faultsFor(e.id()).stream())
						.map(f -> hit(f, reg, monthKnown)).flatMap(Optional::stream).toList();

		return new CarMatch(generation, candidates, generation.isEmpty() && !candidates.isEmpty(), engine, faults,
				gearbox(l, candidates), faults.stream().anyMatch(FaultHit::uncertain));
	}

	private Optional<Engine> engine(Listing l, List<Generation> candidates) {
		Stream<Engine> pool;
		if (!candidates.isEmpty()) {
			pool = candidates.stream().flatMap(g -> kb.enginesFor(g.id()).stream()).distinct();
		} else if (l.getMake() != null) {
			pool = kb.enginesForMake(l.getMake()).stream();
		} else {
			return Optional.empty();
		}
		Integer kw = l.getPowerCv() == null ? null : (int) Math.round(l.getPowerCv() / 1.36);
		List<Engine> kept = pool
				.filter(e -> e.fuel() == null || l.getFuel() == null || e.fuel().equals(l.getFuel().name()))
				.filter(e -> e.cc() == null || l.getCc() == null || Math.abs(e.cc() - l.getCc()) <= 5)
				.filter(e -> e.kwList().isEmpty() || kw == null || e.kwList().stream().anyMatch(k -> Math.abs(k - kw) <= 2))
				.toList();
		return kept.size() == 1 ? Optional.of(kept.getFirst()) : Optional.empty();
	}

	private static Optional<FaultHit> hit(EngineFault f, YearMonth reg, boolean monthKnown) {
		YearMonth from = f.buildFrom();
		YearMonth to = f.buildTo();
		if ((from != null && reg.isBefore(from)) || (to != null && reg.isAfter(to.plusMonths(EDGE_MONTHS)))) {
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
			Pattern word = Pattern.compile("(?<![\\p{Alnum}])" + Pattern.quote(Text.normalise(rule.keyword()))
					+ "(?![\\p{Alnum}])");
			texts.stream().filter(t -> word.matcher(Text.normalise(t)).find()).findFirst()
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
