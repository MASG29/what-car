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
	 * Accepts only https://www.standvirtual.com/carros/anuncio/... ad pages. Marketplace item links
	 * (https://www.facebook.com/marketplace/item/&lt;id&gt;/) are to be recognised here, with the same strictness, once a
	 * Marketplace source exists; until then they get "Site ainda não suportado" like any other site.
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
		if (!"https".equalsIgnoreCase(uri.getScheme()) || !STANDVIRTUAL_HOST.equalsIgnoreCase(uri.getHost())
				|| uri.getRawUserInfo() != null || uri.getPort() != -1 || path == null
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
