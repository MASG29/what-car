package carfinder.data;

import carfinder.listing.Site;
import java.time.Instant;

/** One import: when, what the file held, and how many of its ads are still stored. */
public record ImportBatchView(long id, Site site, Instant importedAt, int read, int added, int listings) {
}
