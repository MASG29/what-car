package carfinder.scoring;

import carfinder.knowledge.Generation;
import carfinder.knowledge.Text;
import carfinder.listing.Listing;
import carfinder.matching.CarMatch;
import java.time.YearMonth;
import java.util.List;
import java.util.OptionalDouble;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Compares an ad's price with the median of comparable ads in the pool. With fewer than 3, the comparison widens
 * in steps (drop the km rule, then also allow +-3 years) and stops at the first step with enough ads.
 */
@Component
@EnableConfigurationProperties(PriceProperties.class)
public class PriceScorer {

	private static final int MIN_COMPARABLES = 3;
	private static final int PLACEHOLDER_BELOW_EUR = 100;
	private static final int MAX_KM_GAP = 50_000;
	private static final double WIDENED_CONFIDENCE = 0.7;

	/** Year gap and km rule per step, strictest first. */
	private record Step(int maxYearGap, boolean kmRule) {
	}

	private static final List<Step> STEPS = List.of(new Step(2, true), new Step(2, false), new Step(3, false));

	private final PriceProperties props;

	public PriceScorer(PriceProperties props) {
		this.props = props;
	}

	public PriceResult score(Listing l, CarMatch m, List<Listing> pool) {
		if (!realPrice(l) || l.getYear() == null) {
			return none(0, l.getYear() == null ? "ano não indicado" : "preço não indicado");
		}
		// ponytail: make aliases ("vw") are not unified across ads; only the generation window uses the KB.
		List<Integer> prices = List.of();
		int step = 0;
		for (; step < STEPS.size(); step++) {
			Step st = STEPS.get(step);
			prices = pool.stream().filter(p -> comparable(l, m, p, st)).map(Listing::getPriceEur).sorted().toList();
			if (prices.size() >= MIN_COMPARABLES) {
				break;
			}
		}
		int n = prices.size();
		if (n < MIN_COMPARABLES) {
			return none(n, "poucos anúncios comparáveis (" + n + ")");
		}
		boolean widened = step > 0;
		double median = n % 2 == 1 ? prices.get(n / 2) : (prices.get(n / 2 - 1) + prices.get(n / 2)) / 2.0;
		double ratio = l.getPriceEur() / median;
		SubScore s = new SubScore(OptionalDouble.of(Ratio.toScore(ratio)),
				Math.min(1.0, n / 10.0) * (widened ? WIDENED_CONFIDENCE : 1.0),
				"%d € vs mediana %.0f € de %d anúncios comparáveis".formatted(l.getPriceEur(), median, n)
						+ (widened ? " (comparação alargada)" : ""),
				List.of(), List.of());
		// A widened comparison ignores km, so it scores but never drops an ad or excuses km over the limit.
		return new PriceResult(s, OptionalDouble.of(ratio), !widened && ratio > props.inflatedRatio(),
				!widened && ratio <= props.wellBelowRatio());
	}

	private static PriceResult none(int n, String why) {
		return new PriceResult(new SubScore(OptionalDouble.empty(), 0, why, List.of(), List.of()),
				OptionalDouble.empty(), false, false);
	}

	private static boolean realPrice(Listing p) {
		return p.getPriceEur() != null && p.getPriceEur() >= PLACEHOLDER_BELOW_EUR;
	}

	private static boolean comparable(Listing l, CarMatch m, Listing p, Step st) {
		if (p.getUrl().equals(l.getUrl()) || p.isParts() || !realPrice(p) || p.getYear() == null) {
			return false;
		}
		if (!sameName(l.getMake(), p.getMake()) || !sameName(l.getModel(), p.getModel())) {
			return false;
		}
		if (Math.abs(p.getYear() - l.getYear()) > st.maxYearGap()) {
			return false;
		}
		if (st.kmRule() && l.getKm() != null && p.getKm() != null && Math.abs(p.getKm() - l.getKm()) > MAX_KM_GAP) {
			return false;
		}
		return m.generation().map(g -> g.covers(registration(p))).orElse(true);
	}

	private static boolean sameName(String a, String b) {
		return a != null && b != null && Text.normalise(a).equals(Text.normalise(b));
	}

	private static YearMonth registration(Listing p) {
		int month = p.getMonth() != null && p.getMonth() >= 1 && p.getMonth() <= 12 ? p.getMonth() : 6;
		return YearMonth.of(p.getYear(), month);
	}
}
