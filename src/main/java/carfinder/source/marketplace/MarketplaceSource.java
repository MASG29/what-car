package carfinder.source.marketplace;

import carfinder.listing.RawListing;
import carfinder.listing.Site;
import carfinder.source.ListingSource;
import carfinder.source.Pace;
import carfinder.source.SearchQuery;
import carfinder.source.SourceException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Reads public Marketplace item pages logged out: plain HTTP, no cookie handler (so no cookie is ever sent), no
 * account. The feed comes from HAR imports, so {@link #search} reads nothing unless the user turned on the automated
 * search (secondary account, {@link MarketplaceBrowser}).
 */
@Component
public class MarketplaceSource implements ListingSource {

	private static final Pattern ITEM_PATH = Pattern.compile("^/marketplace/item/\\d+/?$");

	private final MarketplaceProperties props;
	private final Pace pace;
	private final MarketplaceAdvertParser parser;
	private final MarketplaceFeedParser feedParser;
	private final MarketplaceBrowser browser;
	private final HttpClient http = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NEVER)
			.connectTimeout(Duration.ofSeconds(15))
			.build();
	private boolean requested;

	public MarketplaceSource(MarketplaceProperties props, @Qualifier("marketplacePace") Pace pace,
			MarketplaceAdvertParser parser, MarketplaceFeedParser feedParser, MarketplaceBrowser browser) {
		this.feedParser = feedParser;
		this.browser = browser;
		this.props = props;
		this.pace = pace;
		this.parser = parser;
	}

	@Override
	public Site site() {
		return Site.MARKETPLACE;
	}

	@Override
	public List<RawListing> search(SearchQuery query, Consumer<String> progress) {
		if (!query.automated()) {
			return List.of();
		}
		List<String> bodies = browser.collect(searchUrl(query), progress);
		if (bodies.isEmpty()) {
			throw new SourceException("A pesquisa automática não recebeu anúncios do Marketplace");
		}
		// ponytail: sold/reserved ids from the feed are dropped here; the HAR import marks them
		return feedParser.parse(bodies.stream()).live();
	}

	/** The same vehicles link the HAR guide gives the user: only maxPrice and query are honoured by Facebook. */
	private String searchUrl(SearchQuery query) {
		String typed = Stream.of(query.make(), query.model()).filter(Objects::nonNull).collect(Collectors.joining(" "));
		return props.baseUrl() + "/marketplace/category/vehicles?maxPrice=" + query.maxPriceEur()
				+ (typed.isEmpty() ? "" : "&query=" + URLEncoder.encode(typed, StandardCharsets.UTF_8)) + "&exact=false";
	}

	@Override
	public RawListing details(String url) {
		return parser.parse(get(URI.create(url)), url);
	}

	@Override
	public int maxDetailsPerRun() {
		return props.maxDetails() == null ? Integer.MAX_VALUE : props.maxDetails();
	}

	private synchronized String get(URI uri) {
		URI base = URI.create(props.baseUrl());
		if (!base.getScheme().equalsIgnoreCase(String.valueOf(uri.getScheme()))
				|| !base.getHost().equalsIgnoreCase(String.valueOf(uri.getHost())) || base.getPort() != uri.getPort()
				|| uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
				|| uri.getRawPath() == null || !ITEM_PATH.matcher(uri.getRawPath()).matches()) {
			throw new IllegalStateException("Not a Marketplace item page: " + uri);
		}
		if (requested) {
			pace.between();
		}
		requested = true;
		// The headers every browser sends on a top-level page load; without them Facebook answers 400.
		HttpRequest request = HttpRequest.newBuilder(uri)
				.header("User-Agent", props.userAgent())
				.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
				.header("Accept-Language", "pt-PT,pt;q=0.9")
				.header("Upgrade-Insecure-Requests", "1")
				.header("Sec-Fetch-Dest", "document")
				.header("Sec-Fetch-Mode", "navigate")
				.header("Sec-Fetch-Site", "none")
				.header("Sec-Fetch-User", "?1")
				.timeout(Duration.ofSeconds(30))
				.GET().build();
		try {
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			int status = response.statusCode();
			if (status / 100 == 3 && response.headers().firstValue("Location").filter(MarketplaceSource::isLogin)
					.isPresent()) {
				throw new LoginWallException();
			}
			if (status != 200) {
				throw new SourceException("Marketplace respondeu com erro HTTP " + status);
			}
			return response.body();
		}
		catch (IOException e) {
			throw new SourceException("Não foi possível contactar o Marketplace", e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new SourceException("Pesquisa interrompida", e);
		}
	}

	private static boolean isLogin(String location) {
		try {
			String path = URI.create(location).getPath();
			return path != null && path.startsWith("/login");
		}
		catch (IllegalArgumentException e) {
			return false;
		}
	}
}
