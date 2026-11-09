package carfinder.source.marketplace;

import carfinder.listing.RawListing;
import carfinder.listing.Site;
import carfinder.source.ListingSource;
import carfinder.source.Pace;
import carfinder.source.SearchQuery;
import carfinder.source.SourceException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Reads public Marketplace item pages logged out: plain HTTP, no cookie handler (so no cookie is ever sent), no
 * account. The feed itself comes from HAR imports, so {@link #search} reads nothing.
 */
@Component
public class MarketplaceSource implements ListingSource {

	private static final Pattern ITEM_PATH = Pattern.compile("^/marketplace/item/\\d+/?$");

	private final MarketplaceProperties props;
	private final Pace pace;
	private final MarketplaceAdvertParser parser;
	private final HttpClient http = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NEVER)
			.connectTimeout(Duration.ofSeconds(15))
			.build();
	private boolean requested;

	public MarketplaceSource(MarketplaceProperties props, @Qualifier("marketplacePace") Pace pace,
			MarketplaceAdvertParser parser) {
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
		return List.of();
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
