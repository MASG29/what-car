package carfinder.scoring;

import carfinder.buying.Checklist;
import carfinder.buying.RealCost;
import carfinder.listing.Listing;
import java.util.List;
import java.util.Map;

/** rank = total x (0.5 + 0.5 x confidence). notesPt come from the filter; warningsPt from the sub-scores. */
public record ScoredListing(Listing listing, List<String> alsoOnUrls, Map<Criterion, SubScore> subs, double total,
		double confidence, double rank, boolean top5, List<String> warningsPt, List<String> notesPt,
		RealCost realCost, Checklist checklist) {
}
