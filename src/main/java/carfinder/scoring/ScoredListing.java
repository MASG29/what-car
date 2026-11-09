package carfinder.scoring;

import carfinder.buying.Checklist;
import carfinder.buying.RealCost;
import carfinder.listing.Listing;
import java.util.List;
import java.util.Map;

/** rank = (sum w*v over present / sum of all w) x (0.5 + 0.5 x confidence); total re-normalises over present. notesPt come from the filter and maintenance hints; warningsPt from the sub-scores and problem hints. rank includes the condition adjustment (floor 0); total does not. suspect: price well below comparable ads (hidden damage possible); never top 5. */
public record ScoredListing(Listing listing, List<String> alsoOnUrls, Map<Criterion, SubScore> subs, double total,
		double confidence, double rank, boolean top5, boolean suspect, List<String> warningsPt, List<String> notesPt,
		RealCost realCost, Checklist checklist, ConditionResult condition, String whyPt) {
}
