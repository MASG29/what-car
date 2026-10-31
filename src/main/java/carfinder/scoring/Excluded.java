package carfinder.scoring;

import carfinder.listing.Listing;
import java.util.List;

/** An ad the filter dropped, with every reason in PT. */
public record Excluded(Listing listing, List<String> reasonsPt) {
}
