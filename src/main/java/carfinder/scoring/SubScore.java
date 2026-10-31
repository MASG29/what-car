package carfinder.scoring;

import java.util.List;
import java.util.OptionalDouble;

/** value and confidence are in 0..1. An empty value means "sem dados", never a neutral number. */
public record SubScore(OptionalDouble value, double confidence, String explanationPt, List<String> sourceUrls,
		List<String> warningsPt) {
}
