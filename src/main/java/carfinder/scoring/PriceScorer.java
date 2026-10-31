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

/** Compares an ad's price with the median of comparable ads in the pool. */
@Component
@EnableConfigurationProperties(PriceProperties.class)
public class PriceScorer {

	private static final int MIN_COMPARABLES = 3;
	private static final int PLACEHOLDER_BELOW_EUR = 100;
	private static final int MAX_YEAR_GAP = 2;
	private static final int MAX_KM_GAP = 50_000;

	private final PriceProperties props;

	PriceScorer(PriceProperties props) {
		this.props = props;
	}

	public PriceResult score(Listing l, CarMatch m, List<Listing> pool) {
		if (!realPrice(l) || l.getYear() == null) {
			return none(0, l.getYear() == null ? "ano não indicado" : "preço não indicado");
		}
		// ponytail: make aliases ("vw") are not unified across ads; only the generation window uses the KB.
		List<Integer> prices = pool.stream().filter(p -> comparable(l, m, p)).map(Listing::getPriceEur).sorted()
				.toList();
		int n = prices.size();
		if (n < MIN_COMPARABLES) {
			return none(n, "poucos anúncios comparáveis (" + n + ")");
		}
		double median = n % 2 == 1 ? prices.get(n / 2) : (prices.get(n / 2 - 1) + prices.get(n / 2)) / 2.0;
		double ratio = l.getPriceEur() / median;
		SubScore s = new SubScore(OptionalDouble.of(Ratio.toScore(ratio)), Math.min(1.0, n / 10.0),
				"%d € vs mediana %.0f € de %d anúncios comparáveis".formatted(l.getPriceEur(), median, n), List.of(),
				List.of());
		return new PriceResult(s, OptionalDouble.of(ratio), ratio > props.inflatedRatio(),
				ratio <= props.wellBelowRatio());
	}

	private static PriceResult none(int n, String why) {
		return new PriceResult(new SubScore(OptionalDouble.empty(), 0, why, List.of(), List.of()),
				OptionalDouble.empty(), false, false);
	}

	private static boolean realPrice(Listing p) {
		return p.getPriceEur() != null && p.getPriceEur() >= PLACEHOLDER_BELOW_EUR;
	}

	private static boolean comparable(Listing l, CarMatch m, Listing p) {
		if (p.getUrl().equals(l.getUrl()) || p.isParts() || !realPrice(p) || p.getYear() == null) {
			return false;
		}
		if (!sameName(l.getMake(), p.getMake()) || !sameName(l.getModel(), p.getModel())) {
			return false;
		}
		if (Math.abs(p.getYear() - l.getYear()) > MAX_YEAR_GAP) {
			return false;
		}
		if (l.getKm() != null && p.getKm() != null && Math.abs(p.getKm() - l.getKm()) > MAX_KM_GAP) {
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
