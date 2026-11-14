package carfinder.web;

import carfinder.data.DataStore;
import carfinder.listing.ListingRepository;
import carfinder.listing.ListingStore;
import carfinder.listing.RawListing;
import carfinder.listing.Site;
import carfinder.search.SearchService;
import carfinder.source.SourceException;
import carfinder.source.marketplace.HarReader;
import carfinder.source.marketplace.MarketplaceFeedParser;
import carfinder.source.marketplace.MarketplaceFeedParser.FeedResult;
import carfinder.source.olx.OlxFeedParser;
import carfinder.web.dto.ErrorDto;
import carfinder.web.dto.ImportResult;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Imports Marketplace or OLX ads from a HAR file the user saved in their own browser. The request body is the raw
 * file, parsed as it streams in: no multipart, no temp file, nothing of it written or logged. Nothing is requested from
 * either site. Only application/octet-stream is read: it needs a CORS preflight, so another web page cannot post one.
 */
@RestController
public class ImportController {

	private final MarketplaceFeedParser parser;
	private final OlxFeedParser olx;
	private final ListingStore store;
	private final ListingRepository listings;
	private final SearchService search;
	private final DataStore data;

	public ImportController(MarketplaceFeedParser parser, OlxFeedParser olx, ListingStore store,
			ListingRepository listings, SearchService search, DataStore data) {
		this.olx = olx;
		this.data = data;
		this.parser = parser;
		this.store = store;
		this.listings = listings;
		this.search = search;
	}

	@PostMapping(path = "/api/imports/marketplace", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public ImportResult importMarketplace(HttpServletRequest request) {
		return importHar(request, Site.MARKETPLACE, HarReader.MARKETPLACE, parser::parse);
	}

	@PostMapping(path = "/api/imports/olx", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public ImportResult importOlx(HttpServletRequest request) {
		return importHar(request, Site.OLX, OlxFeedParser.HAR, olx::parse);
	}

	private ImportResult importHar(HttpServletRequest request, Site site, HarReader.Filter filter,
			Function<Stream<String>, FeedResult> parse) {
		return search.whileIdle(() -> {
			FeedResult feed;
			try (InputStream in = request.getInputStream(); Stream<String> bodies = HarReader.bodies(in, filter)) {
				feed = parse.apply(bodies);
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			int added = 0;
			List<Long> ids = new ArrayList<>();
			for (RawListing r : feed.live()) {
				if (listings.findBySiteAndExternalId(site, r.externalId()).isEmpty()) {
					added++;
				}
				ids.add(store.upsert(r).getId());
			}
			feed.unavailableIds().forEach(id -> store.markUnavailable(site, id));
			int read = feed.live().size() + feed.unavailableIds().size();
			data.recordImport(site, read, added, ids);
			return new ImportResult(read, added, feed.unavailableIds().size(), feed.skipped());
		});
	}

	/** Not a HAR, no bodies of that site, or a changed format; the message is PT-PT and quotes nothing from the file. */
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
