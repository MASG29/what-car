package carfinder.scoring;

import carfinder.knowledge.Text;
import carfinder.listing.Listing;
import java.util.List;
import java.util.OptionalDouble;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class CompletenessScorer {

	private static final Pattern MAINTENANCE = Pattern.compile("correia|distribui|revis|fatura|manuten");
	private static final int FIELDS = 7;

	public SubScore score(Listing l) {
		int present = (int) Stream.of(l.getKm(), l.getYear(), l.getCc(), l.getPowerCv(), l.getFuel(), l.getGearbox(),
				l.getDoors()).filter(v -> v != null).count();
		String d = l.getDescription();
		double value = 0.8 * present / FIELDS + (d != null && d.length() >= 100 ? 0.2 : 0);
		if (d != null && MAINTENANCE.matcher(Text.normalise(d)).find()) {
			value += 0.1;
		}
		// Doors and description exist only on the advert page, so a search-page ad is scored with less confidence.
		double confidence = l.getDetailFetchedAt() == null ? 0.5 : 1.0;
		List<String> warnings = l.getKm() == null ? List.of("km não indicados") : List.of();
		return new SubScore(OptionalDouble.of(Math.min(1.0, value)), confidence,
				present + "/" + FIELDS + " campos preenchidos", List.of(), warnings);
	}
}
