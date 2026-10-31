package carfinder.source.standvirtual;

import carfinder.listing.RawListing;
import java.util.List;

public record SearchPage(int totalCount, int pageSize, List<RawListing> listings) {
}
