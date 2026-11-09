package carfinder.source;

import carfinder.listing.RawListing;
import carfinder.listing.Site;
import java.util.List;
import java.util.function.Consumer;

public interface ListingSource {

	Site site();

	/** Reads the search pages within the page budget; {@code progress} gets PT-PT status lines. */
	List<RawListing> search(SearchQuery query, Consumer<String> progress);

	/** Reads one advert page; a removed ad throws {@link SourceException}. */
	RawListing details(String url);

	/** How many advert pages one run may read from this site. */
	default int maxDetailsPerRun() {
		return Integer.MAX_VALUE;
	}
}
