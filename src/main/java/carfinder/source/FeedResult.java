package carfinder.source;

import carfinder.listing.RawListing;
import java.util.List;

/** Live vehicles (one per id), the ids seen sold or reserved, and ads left out on purpose (another site's copies). */
public record FeedResult(List<RawListing> live, List<String> unavailableIds, int skipped) {

	public FeedResult(List<RawListing> live, List<String> unavailableIds) {
		this(live, unavailableIds, 0);
	}
}
