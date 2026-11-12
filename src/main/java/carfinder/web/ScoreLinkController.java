package carfinder.web;

import carfinder.listing.Site;
import carfinder.search.SearchService;
import carfinder.source.NeedsUserException;
import carfinder.source.SourceException;
import carfinder.web.dto.ErrorDto;
import carfinder.web.dto.RankingDto;
import carfinder.web.dto.ScoreLinkRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Scores one pasted ad link. Only URLs that pass {@link #parse} reach a source, which fetches what it is given. */
@RestController
public class ScoreLinkController {

	static final String UNSUPPORTED = "Site ainda não suportado";
	private static final String STANDVIRTUAL_HOST = "www.standvirtual.com";
	private static final String STANDVIRTUAL_AD_PATH = "/carros/anuncio/";
	private static final String MARKETPLACE_HOST = "www.facebook.com";
	private static final Pattern MARKETPLACE_ITEM = Pattern.compile("^/marketplace/item/(\\d+)/?$");
	private static final String CUSTOJUSTO_HOST = "www.custojusto.pt";
	private static final Pattern CUSTOJUSTO_AD = Pattern
			.compile("^/[a-z0-9-]+/veiculos/carros-usados/[a-z0-9-]+/[a-z0-9-]+-\\d+$");

	private final SearchService service;

	public ScoreLinkController(SearchService service) {
		this.service = service;
	}

	/** The site and the clean URL to fetch (no query, fragment, user info or port). */
	record Link(Site site, String url) {
	}

	@PostMapping("/api/score-link")
	public RankingDto score(@Valid @RequestBody ScoreLinkRequest req) {
		Link link = parse(req.url());
		return RankingDto.from(service.scoreLink(link.site(), link.url(), req.limits(), req.preset(),
				req.customWeights()));
	}

	/**
	 * Accepts only https://www.standvirtual.com/carros/anuncio/... ad pages and
	 * https://www.facebook.com/marketplace/item/&lt;digits&gt;/ items and
	 * https://www.custojusto.pt/&lt;region&gt;/veiculos/carros-usados/&lt;type&gt;/&lt;slug&gt;-&lt;digits&gt; ads: exact host, https, no user info or port. The
	 * URL is rebuilt without query or fragment.
	 */
	static Link parse(String url) {
		URI uri;
		try {
			uri = URI.create(url.strip());
		}
		catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(UNSUPPORTED);
		}
		String path = uri.getPath();
		if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null || uri.getPort() != -1) {
			throw new IllegalArgumentException(UNSUPPORTED);
		}
		if (MARKETPLACE_HOST.equalsIgnoreCase(uri.getHost())) {
			Matcher m = MARKETPLACE_ITEM.matcher(String.valueOf(uri.getRawPath()));
			if (!m.matches()) {
				throw new IllegalArgumentException(UNSUPPORTED);
			}
			return new Link(Site.MARKETPLACE, "https://" + MARKETPLACE_HOST + "/marketplace/item/" + m.group(1) + "/");
		}
		if (CUSTOJUSTO_HOST.equalsIgnoreCase(uri.getHost())) {
			if (!CUSTOJUSTO_AD.matcher(String.valueOf(uri.getRawPath())).matches()) {
				throw new IllegalArgumentException(UNSUPPORTED);
			}
			return new Link(Site.CUSTOJUSTO, "https://" + CUSTOJUSTO_HOST + uri.getRawPath());
		}
		if (!STANDVIRTUAL_HOST.equalsIgnoreCase(uri.getHost()) || path == null
				|| !path.startsWith(STANDVIRTUAL_AD_PATH) || path.length() == STANDVIRTUAL_AD_PATH.length()
				|| path.contains("..") || path.contains("//")) {
			throw new IllegalArgumentException(UNSUPPORTED);
		}
		return new Link(Site.STANDVIRTUAL, "https://" + STANDVIRTUAL_HOST + uri.getRawPath());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	ErrorDto unsupported(IllegalArgumentException e) {
		return new ErrorDto(e.getMessage());
	}

	/** The ad page could not be read (removed ad, site error); the message is PT-PT. */
	@ExceptionHandler(SourceException.class)
	@ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
	ErrorDto unreadable(SourceException e) {
		return new ErrorDto(e.getMessage());
	}

	@ExceptionHandler(NeedsUserException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	ErrorDto needsUser(NeedsUserException e) {
		return new ErrorDto(e.getMessage());
	}
}
