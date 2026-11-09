package carfinder.web;

import carfinder.listing.ListingRepository;
import carfinder.listing.ListingStore;
import carfinder.listing.RawListing;
import carfinder.listing.Site;
import carfinder.search.SearchService;
import carfinder.source.SourceException;
import carfinder.source.marketplace.HarReader;
import carfinder.source.marketplace.MarketplaceFeedParser;
import carfinder.source.marketplace.MarketplaceFeedParser.FeedResult;
import carfinder.web.dto.ErrorDto;
import carfinder.web.dto.ImportResult;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Imports Marketplace ads from a HAR file the user saved in their own browser. The request body is the raw file,
 * parsed as it streams in: no multipart, no temp file, nothing of it written or logged. Nothing is requested from
 * Facebook.
 */
@RestController
public class ImportController {

	private final MarketplaceFeedParser parser;
	private final ListingStore store;
	private final ListingRepository listings;
	private final SearchService search;

	public ImportController(MarketplaceFeedParser parser, ListingStore store, ListingRepository listings,
			SearchService search) {
		this.parser = parser;
		this.store = store;
		this.listings = listings;
		this.search = search;
	}

	@PostMapping("/api/imports/marketplace")
	public ImportResult importMarketplace(HttpServletRequest request) {
		return search.whileIdle(() -> {
			FeedResult feed;
			try (InputStream in = request.getInputStream(); Stream<String> bodies = HarReader.bodies(in)) {
				feed = parser.parse(bodies);
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			int added = 0;
			for (RawListing r : feed.live()) {
				if (listings.findBySiteAndExternalId(Site.MARKETPLACE, r.externalId()).isEmpty()) {
					added++;
				}
				store.upsert(r);
			}
			feed.unavailableIds().forEach(id -> store.markUnavailable(Site.MARKETPLACE, id));
			return new ImportResult(feed.live().size() + feed.unavailableIds().size(), added,
					feed.unavailableIds().size());
		});
	}

	/** Not a HAR, no Marketplace bodies, or a changed format; the message is PT-PT and quotes nothing from the file. */
	@ExceptionHandler(SourceException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	ErrorDto unreadable(SourceException e) {
		return new ErrorDto(e.getMessage());
	}

	@ExceptionHandler(IllegalStateException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	ErrorDto busy(IllegalStateException e) {
		return new ErrorDto(e.getMessage());
	}
}
