package carfinder.scoring;

import carfinder.buying.ChecklistBuilder;
import carfinder.buying.RealCost;
import carfinder.buying.RealCostCalculator;
import carfinder.listing.Listing;
import carfinder.matching.CarMatch;
import carfinder.matching.CarMatcher;
import carfinder.search.Deduplicator;
import carfinder.search.FilterResult;
import carfinder.search.ListingFilter;
import carfinder.search.SearchLimits;
import java.time.Clock;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Match, score, cost, filter, group duplicates, then rank by weighted score adjusted by confidence, plus the
 * condition hints read from the description (floor 0). The hints never change a sub-score or the shown total.
 */
@Component
public class ScoringService {

	/** No age band applies when the year is unknown. */
	private static final int AGE_UNKNOWN = -1;
	/** Builder's call, not a sourced figure: heavier than any cosmetic problem, applied when damaged ads are shown. */
	static final double DAMAGE_PENALTY = 0.15;

	private final CarMatcher matcher;
	private final ReliabilityScorer reliability;
	private final PriceScorer price;
	private final ConsumptionScorer consumption;
	private final CompletenessScorer completeness;
	private final PhotoScorer photos;
	private final RealCostCalculator realCost;
	private final ChecklistBuilder checklist;
	private final ListingFilter filter;
	private final Clock clock;

	public ScoringService(CarMatcher matcher, ReliabilityScorer reliability, PriceScorer price,
			ConsumptionScorer consumption, CompletenessScorer completeness, PhotoScorer photos,
			RealCostCalculator realCost, ChecklistBuilder checklist, ListingFilter filter, Clock clock) {
		this.matcher = matcher;
		this.reliability = reliability;
		this.price = price;
		this.consumption = consumption;
		this.completeness = completeness;
		this.photos = photos;
		this.realCost = realCost;
		this.checklist = checklist;
		this.filter = filter;
		this.clock = clock;
	}

	/** ranked is sorted by rank, best first; excluded keeps every dropped ad with its reasons. */
	public record Ranking(List<ScoredListing> ranked, List<Excluded> excluded) {
	}

	record Totals(double total, double confidence, double rank) {
	}

	private record Kept(CarMatch match, Map<Criterion, SubScore> subs, RealCost cost, List<String> notes) {
	}

	public Ranking rank(List<Listing> candidates, List<Listing> pricePool, SearchLimits s, Preset preset,
			Weights custom) {
		Weights w = preset.weights(custom);
		int thisYear = Year.now(clock).getValue();
		Map<Listing, Kept> kept = new IdentityHashMap<>();
		List<Listing> keptOrder = new ArrayList<>();
		List<Excluded> excluded = new ArrayList<>();
		for (Listing l : candidates) {
			CarMatch m = matcher.match(l);
			PriceResult p = price.score(l, m, pricePool);
			Map<Criterion, SubScore> subs = new EnumMap<>(Criterion.class);
			subs.put(Criterion.PRICE, p.score());
			subs.put(Criterion.RELIABILITY,
					reliability.score(m, l, l.getYear() == null ? AGE_UNKNOWN : thisYear - l.getYear()));
			subs.put(Criterion.CONSUMPTION, consumption.score(m));
			subs.put(Criterion.COMPLETENESS, completeness.score(l));
			subs.put(Criterion.PHOTOS, photos.score(l));
			RealCost rc = realCost.estimate(l, m, s.realCost());
			FilterResult f = filter.apply(l, m, p, rc, s);
			if (!f.keep()) {
				excluded.add(new Excluded(l, f.notesPt()));
				continue;
			}
			kept.put(l, new Kept(m, subs, rc, f.notesPt()));
			keptOrder.add(l);
		}

		List<ScoredListing> scored = new ArrayList<>();
		for (List<Listing> group : Deduplicator.group(keptOrder)) {
			Listing l = group.getFirst();
			Kept k = kept.get(l);
			Totals t = combine(k.subs(), w);
			ConditionResult condition = ConditionReader.read(l.getTitle(), l.getDescription());
			List<String> warnings = Stream.concat(k.subs().values().stream().flatMap(x -> x.warningsPt().stream()),
					Stream.concat(condition.problemsPt().stream().map(p -> "O anúncio menciona: " + p),
							condition.damagePt().isEmpty() ? Stream.<String>empty()
									: Stream.of("Precisa de reparação: " + String.join(", ", condition.damagePt()))))
					.distinct().toList();
			List<String> notes = Stream.concat(k.notes().stream(),
					condition.maintenancePt().stream().map(m -> "Manutenção recente: " + m)).toList();
			List<String> alsoOn = group.subList(1, group.size()).stream().map(Listing::getUrl).toList();
			scored.add(new ScoredListing(l, alsoOn, Collections.unmodifiableMap(k.subs()), t.total(), t.confidence(),
					Math.max(0, t.rank() + condition.rankAdjustment() - damagePenalty(condition)), false, warnings, notes, k.cost(),
					checklist.build(l, k.match()), condition, null));
		}
		scored.sort(Comparator.comparingDouble(ScoredListing::rank).reversed());
		List<ScoredListing> ranked = new ArrayList<>();
		for (int i = 0; i < scored.size(); i++) {
			ScoredListing x = scored.get(i);
			ranked.add(new ScoredListing(x.listing(), x.alsoOnUrls(), x.subs(), x.total(), x.confidence(), x.rank(),
					i < 5, x.warningsPt(), x.notesPt(), x.realCost(), x.checklist(), x.condition(),
					WhyText.build(x, i + 1)));
		}
		return new Ranking(List.copyOf(ranked), List.copyOf(excluded));
	}

	static double damagePenalty(ConditionResult c) {
		return c.damagePt().isEmpty() ? 0 : DAMAGE_PENALTY;
	}

	/**
	 * total (shown) re-normalises over present values (a missing value is left out, never a neutral number). rank
	 * divides by every weight, so a missing criterion adds nothing, then scales by confidence: a sparse ad cannot
	 * outrank a documented one just because data is missing.
	 */
	static Totals combine(Map<Criterion, SubScore> subs, Weights w) {
		double weighted = 0;
		double presentWeight = 0;
		double conf = 0;
		double allWeight = 0;
		for (Criterion c : Criterion.values()) {
			double wc = w.of(c);
			SubScore s = subs.get(c);
			allWeight += wc;
			if (s == null) {
				continue;
			}
			conf += wc * s.confidence();
			if (s.value().isPresent()) {
				weighted += wc * s.value().getAsDouble();
				presentWeight += wc;
			}
		}
		double total = presentWeight > 0 ? weighted / presentWeight : 0;
		double confidence = allWeight > 0 ? conf / allWeight : 0;
		double rankBase = allWeight > 0 ? weighted / allWeight : 0;
		return new Totals(total, confidence, rankBase * (0.5 + 0.5 * confidence));
	}
}
