package carfinder.source.standvirtual;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/** Reads public Standvirtual search and advert pages over plain HTTP, slowly and within a page budget. */
@Component
public class StandvirtualSource implements ListingSource {

	private static final List<String> FORBIDDEN = List.of("/api/", "/ajax/", "/account/", "/ad/contact/");

	private final StandvirtualProperties props;
	private final Pace pace;
	private final HttpClient http = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NEVER)
			.connectTimeout(Duration.ofSeconds(15))
			.build();
	private final StandvirtualParser searchParser = new StandvirtualParser();
	private final StandvirtualAdvertParser advertParser = new StandvirtualAdvertParser();
	private boolean requested;

	public StandvirtualSource(StandvirtualProperties props, Pace pace) {
		this.props = props;
		this.pace = pace;
	}

	@Override
	public Site site() {
		return Site.STANDVIRTUAL;
	}

	@Override
	public List<RawListing> search(SearchQuery query, Consumer<String> progress) {
		Map<String, RawListing> byId = new LinkedHashMap<>();
		int page = 1;
		while (true) {
			SearchPage result = searchParser.parseSearchPage(
					get(StandvirtualUrls.search(URI.create(props.baseUrl()), query.maxPriceEur(), page)));
			result.listings().forEach(l -> byId.putIfAbsent(l.externalId(), l));
			int totalPages = (int) Math.ceil((double) result.totalCount() / result.pageSize());
			progress.accept("Standvirtual: página " + page + " de " + Math.min(totalPages, props.maxPages())
					+ " (" + byId.size() + " anúncios)");
			// Stop by page count, never by "id already seen": edge 0 can be a premium ad out of order.
			// An empty page ends the search even when totalCount says there is more.
			if (result.listings().isEmpty() || (long) page * result.pageSize() >= result.totalCount()
					|| page >= props.maxPages()) {
				return List.copyOf(byId.values());
			}
			page++;
		}
	}

	@Override
	public RawListing details(String url) {
		return advertParser.parse(get(URI.create(url)), url);
	}

	private synchronized String get(URI uri) {
		URI base = URI.create(props.baseUrl());
		if (!base.getScheme().equalsIgnoreCase(String.valueOf(uri.getScheme()))
				|| !base.getHost().equalsIgnoreCase(String.valueOf(uri.getHost())) || base.getPort() != uri.getPort()
				|| uri.getUserInfo() != null) {
			throw new IllegalStateException("Not the configured Standvirtual host: " + uri);
		}
		String path = uri.getPath() == null ? "" : uri.getPath();
		if (FORBIDDEN.stream().anyMatch(path::startsWith)) {
			throw new IllegalStateException("Forbidden Standvirtual path: " + path);
		}
		if (requested) {
			pace.between();
		}
		requested = true;
		HttpRequest request = HttpRequest.newBuilder(uri)
				.header("User-Agent", props.userAgent())
				.header("Accept-Language", "pt-PT,pt;q=0.9")
				.timeout(Duration.ofSeconds(30))
				.GET().build();
		try {
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				throw new SourceException("Standvirtual respondeu com erro HTTP " + response.statusCode());
			}
			return response.body();
		}
		catch (IOException e) {
			throw new SourceException("Não foi possível contactar o Standvirtual", e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new SourceException("Pesquisa interrompida", e);
		}
	}
}
