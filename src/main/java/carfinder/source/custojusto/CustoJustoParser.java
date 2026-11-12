package carfinder.source.custojusto;

import carfinder.geo.Places;
import carfinder.knowledge.Generation;
import carfinder.knowledge.KnowledgeBase;
import carfinder.listing.Fuel;
import carfinder.listing.RawListing;
import carfinder.listing.SellerType;
import carfinder.listing.Site;
import carfinder.source.SearchPage;
import carfinder.source.SourceException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads a CustoJusto search page from its embedded {@code __NEXT_DATA__} JSON.
 *
 * <p>Shape (checked 2026-11-12): {@code props.pageProps.listItems[]} with {@code listID}, {@code title}
 * ("Make Model ... - YY"), {@code price}, {@code url} (relative), {@code type} ("sell"; "buy" is a wanted ad),
 * {@code companyAd}, {@code imageCount}, {@code locationNames.district/county} and {@code params.fuel/gearbox/regdate}
 * as labels. Items carry no make, model or km: make and model come from the title, km from the advert page. The
 * totals are {@code initialState.search.resources.totalAds/adsPerpage}. {@code galleryAds} are paid highlights outside
 * the search order and are skipped. The seller's name and id are never read.
 */
@Component
public class CustoJustoParser {

	static final String HOST = "https://www.custojusto.pt";

	private static final Pattern NEXT_DATA = Pattern.compile(
			"<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>", Pattern.DOTALL);
	private static final Pattern YEAR_SUFFIX = Pattern.compile("\\s+-\\s+\\d{2}$");
	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private final KnowledgeBase kb;
	private final Places places;

	public CustoJustoParser(KnowledgeBase kb, Places places) {
		this.kb = kb;
		this.places = places;
	}

	public SearchPage parseSearchPage(String html) {
		JsonNode props = nextData(html).path("props").path("pageProps");
		JsonNode items = props.path("listItems");
		JsonNode resources = props.path("initialState").path("search").path("resources");
		if (!items.isArray() || !resources.path("totalAds").isNumber() || !resources.path("adsPerpage").isNumber()) {
			throw formatChanged(null);
		}
		List<RawListing> listings = new ArrayList<>();
		for (JsonNode item : items) {
			if ("sell".equals(text(item.path("type")))) {
				listings.add(toListing(item));
			}
		}
		return new SearchPage(resources.path("totalAds").asInt(), resources.path("adsPerpage").asInt(), listings);
	}

	private RawListing toListing(JsonNode item) {
		String id = text(item.path("listID"));
		String path = text(item.path("url"));
		String title = text(item.path("title"));
		if (id == null || path == null || title == null) {
			throw formatChanged(null);
		}
		title = YEAR_SUFFIX.matcher(title).replaceFirst("");
		Optional<Generation> g = kb.recogniseMakeModel(title);
		JsonNode params = item.path("params");
		JsonNode where = item.path("locationNames");
		return new RawListing(Site.CUSTOJUSTO, id, HOST + path, title, number(item.path("price")),
				g.map(Generation::make).orElse(null), g.map(Generation::model).orElse(null), null,
				number(params.path("regdate")), null, null, fuel(text(params.path("fuel"))), null, null,
				gearbox(text(params.path("gearbox"))), null, seller(item.path("companyAd")),
				places.district(text(where.path("district"))).orElse(null), text(where.path("county")),
				item.path("imageCount").asInt(0), null, null, false);
	}

	static JsonNode nextData(String html) {
		Matcher m = NEXT_DATA.matcher(html);
		if (!m.find()) {
			throw formatChanged(null);
		}
		try {
			return MAPPER.readTree(m.group(1));
		}
		catch (JacksonException e) {
			throw formatChanged(e);
		}
	}

	/** Non-blank scalar text, otherwise null. */
	static String text(JsonNode n) {
		if (n == null || !n.isValueNode() || n.isNull()) {
			return null;
		}
		String s = n.asString().strip();
		return s.isEmpty() ? null : s;
	}

	/** Digits only, otherwise null; never a default 0. */
	static Integer number(JsonNode n) {
		String s = text(n);
		String d = s == null ? "" : s.replaceAll("\\D", "");
		return d.isEmpty() || d.length() > 9 ? null : Integer.valueOf(d);
	}

	/** The site's whole fuel list (2026-11-12). */
	static Fuel fuel(String label) {
		if (label == null) {
			return null;
		}
		return switch (label) {
			case "Gasolina" -> Fuel.PETROL;
			case "Diesel" -> Fuel.DIESEL;
			case "GPL" -> Fuel.LPG;
			case "ECO/Híbrido (HEV)", "Híbrido plugin" -> Fuel.HYBRID;
			case "Eléctrico" -> Fuel.ELECTRIC;
			default -> Fuel.OTHER;
		};
	}

	/** Manual and Automática map to the app's values; Sequencial and Mista stay as the site says them. */
	static String gearbox(String label) {
		if (label == null) {
			return null;
		}
		return switch (label) {
			case "Manual" -> "manual";
			case "Automática" -> "automatic";
			default -> label;
		};
	}

	static SellerType seller(JsonNode companyAd) {
		return companyAd.isBoolean() ? (companyAd.asBoolean() ? SellerType.DEALER : SellerType.PRIVATE)
				: SellerType.UNKNOWN;
	}

	static SourceException formatChanged(Throwable cause) {
		return new SourceException(Site.CUSTOJUSTO.formatChangedPt(), cause);
	}
}
