package carfinder.source.custojusto;

import carfinder.listing.RawListing;
import carfinder.listing.Site;
import carfinder.source.ListingSource;
import carfinder.source.Pace;
import carfinder.source.SearchPage;
import carfinder.source.SearchQuery;
import carfinder.source.SoldOrReservedException;
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
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Reads public CustoJusto car search and advert pages over plain HTTP, slowly and within a page budget. Only paths
 * under /&lt;region&gt;/veiculos/carros-usados are ever requested; redirects are not followed.
 */
@Component
public class CustoJustoSource implements ListingSource {

	private static final Pattern CAR_PATH = Pattern.compile("^/[a-z0-9-]+/veiculos/carros-usados(/[a-z0-9-]+){0,3}$");

	private final CustoJustoProperties props;
	private final Pace pace;
	private final CustoJustoParser searchParser;
	private final CustoJustoAdvertParser advertParser;
	private final HttpClient http = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NEVER)
			.connectTimeout(Duration.ofSeconds(15))
			.build();
	private boolean requested;

	public CustoJustoSource(CustoJustoProperties props, @Qualifier("custojustoPace") Pace pace,
			CustoJustoParser searchParser, CustoJustoAdvertParser advertParser) {
		this.props = props;
		this.pace = pace;
		this.searchParser = searchParser;
		this.advertParser = advertParser;
	}

	@Override
	public Site site() {
		return Site.CUSTOJUSTO;
	}

	@Override
	public List<RawListing> search(SearchQuery query, Consumer<String> progress) {
		URI base = URI.create(props.baseUrl());
		String make = query.make();
		String model = query.model();
		Map<String, RawListing> byId = new LinkedHashMap<>();
		int page = 1;
		while (true) {
			HttpResponse<String> response = fetch(
					CustoJustoUrls.search(base, query.maxPriceEur(), query.minYear(), page, make, model), true);
			if (page == 1 && make != null && model != null && response.statusCode() / 100 == 3) {
				// the site does not know this make/model slug: search without it, the app filters locally
				make = null;
				model = null;
				continue;
			}
			SearchPage result = searchParser.parseSearchPage(body(response));
			result.listings().forEach(l -> byId.putIfAbsent(l.externalId(), l));
			int totalPages = result.pageSize() == 0 ? 1 : (int) Math.ceil((double) result.totalCount() / result.pageSize());
			progress.accept("CustoJusto: página " + page + " de " + Math.max(1, Math.min(totalPages, props.maxPages()))
					+ " (" + byId.size() + " anúncios)");
			if (result.listings().isEmpty() || (long) page * result.pageSize() >= result.totalCount()
					|| page >= props.maxPages()) {
				return List.copyOf(byId.values());
			}
			page++;
		}
	}

	@Override
	public RawListing details(String url) {
		HttpResponse<String> response = fetch(URI.create(url), false);
		if (response.statusCode() / 100 == 3 && response.headers().firstValue("Location")
				.filter(l -> l.contains("gone=")).isPresent()) {
			throw new SoldOrReservedException("O anúncio do CustoJusto já não está disponível");
		}
		return advertParser.parse(body(response), url);
	}

	@Override
	public int maxDetailsPerRun() {
		return props.maxDetails() == null ? Integer.MAX_VALUE : props.maxDetails();
	}

	private static String body(HttpResponse<String> response) {
		if (response.statusCode() != 200) {
			throw new SourceException("CustoJusto respondeu com erro HTTP " + response.statusCode());
		}
		return response.body();
	}

	private synchronized HttpResponse<String> fetch(URI uri, boolean search) {
		URI base = URI.create(props.baseUrl());
		if (!base.getScheme().equalsIgnoreCase(String.valueOf(uri.getScheme()))
				|| !base.getHost().equalsIgnoreCase(String.valueOf(uri.getHost())) || base.getPort() != uri.getPort()
				|| uri.getRawUserInfo() != null || uri.getRawFragment() != null || (!search && uri.getRawQuery() != null)
				|| uri.getRawPath() == null || !CAR_PATH.matcher(uri.getRawPath()).matches()) {
			throw new IllegalStateException("Not a CustoJusto car page: " + uri);
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
			return http.send(request, HttpResponse.BodyHandlers.ofString());
		}
		catch (IOException e) {
			throw new SourceException("Não foi possível contactar o CustoJusto", e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new SourceException("Pesquisa interrompida", e);
		}
	}
}
