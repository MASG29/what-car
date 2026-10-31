package carfinder.source.standvirtual;

import carfinder.listing.Fuel;
import carfinder.listing.RawListing;
import carfinder.listing.SellerType;
import carfinder.listing.Site;
import carfinder.source.SourceException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads a Standvirtual search page from its embedded {@code __NEXT_DATA__} JSON.
 *
 * <p>Path (checked 2026-10-31): {@code props.pageProps.urqlState} is an object of query-hash entries;
 * each entry's {@code data} is a JSON string, and one of them holds {@code advertSearch} with
 * {@code totalCount}, {@code pageInfo.pageSize} and {@code edges[].node}. The edges can be
 * pageSize + 1: the first may be an {@code isPremiumTopAd} outside the newest-first order.
 *
 * <p>Parameter values seen: {@code fuel_type} = "gaz" (Gasolina), "diesel";
 * {@code gearbox} = "manual", "automatic". Make, model and version use {@code displayValue};
 * the rest use {@code value}.
 */
public class StandvirtualParser {

	static final String FORMAT_CHANGED = "Standvirtual mudou o formato da página";

	private static final Pattern NEXT_DATA = Pattern.compile(
			"<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>", Pattern.DOTALL);

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	public SearchPage parseSearchPage(String html) {
		JsonNode search = findAdvertSearch(nextData(html).path("props").path("pageProps").path("urqlState"));
		JsonNode totalCount = search.path("totalCount");
		JsonNode pageSize = search.path("pageInfo").path("pageSize");
		JsonNode edges = search.path("edges");
		if (!totalCount.isNumber() || !pageSize.isNumber() || !edges.isArray()) {
			throw formatChanged(null);
		}
		List<RawListing> listings = new ArrayList<>();
		for (JsonNode edge : edges) {
			listings.add(toListing(edge.path("node")));
		}
		return new SearchPage(totalCount.asInt(), pageSize.asInt(), listings);
	}

	private JsonNode findAdvertSearch(JsonNode urqlState) {
		for (JsonNode entry : urqlState.values()) {
			JsonNode data = entry.path("data");
			if (data.isString()) {
				JsonNode search = read(data.stringValue()).path("advertSearch");
				if (search.isObject()) {
					return search;
				}
			}
		}
		throw formatChanged(null);
	}

	private RawListing toListing(JsonNode node) {
		String id = text(node.path("id"));
		String url = text(node.path("url"));
		String title = text(node.path("title"));
		if (id == null || url == null || title == null) {
			throw formatChanged(null);
		}
		Map<String, JsonNode> params = new HashMap<>();
		for (JsonNode p : node.path("parameters")) {
			params.put(p.path("key").asString(""), p);
		}
		JsonNode location = node.path("location");
		return new RawListing(Site.STANDVIRTUAL, id, url, title,
				digits(node.path("price").path("amount").path("units")),
				display(params, "make"), display(params, "model"), display(params, "version"),
				digits(value(params, "first_registration_year")), null,
				digits(value(params, "mileage")),
				fuel(text(value(params, "fuel_type"))),
				digits(value(params, "engine_capacity")),
				digits(value(params, "engine_power")),
				text(value(params, "gearbox")),
				null,
				sellerType(text(node.path("seller").path("__typename"))),
				text(location.path("region").path("name")),
				text(location.path("city").path("name")),
				0, null, null, false);
	}

	/** The parsed {@code __NEXT_DATA__} JSON of a page. */
	static JsonNode nextData(String html) {
		Matcher m = NEXT_DATA.matcher(html);
		if (!m.find()) {
			throw formatChanged(null);
		}
		return read(m.group(1));
	}

	private static JsonNode read(String json) {
		try {
			return MAPPER.readTree(json);
		}
		catch (JacksonException e) {
			throw formatChanged(e);
		}
	}

	private static JsonNode value(Map<String, JsonNode> params, String key) {
		JsonNode p = params.get(key);
		return p == null ? null : p.path("value");
	}

	private static String display(Map<String, JsonNode> params, String key) {
		JsonNode p = params.get(key);
		return p == null ? null : text(p.path("displayValue"));
	}

	/** Non-blank scalar text, otherwise null (a blank would overwrite stored detail fields). */
	static String text(JsonNode n) {
		if (n == null || !n.isValueNode() || n.isNull()) {
			return null;
		}
		String s = n.asString().strip();
		return s.isEmpty() ? null : s;
	}

	/** Digits only ("1 299 cm3" -> 1299), otherwise null; never a default 0. */
	static Integer digits(JsonNode n) {
		String s = text(n);
		if (s == null) {
			return null;
		}
		String d = s.replaceAll("\\D", "");
		return d.isEmpty() || d.length() > 9 ? null : Integer.valueOf(d);
	}

	static Fuel fuel(String value) {
		if (value == null) {
			return null;
		}
		return switch (value) {
			case "gaz" -> Fuel.PETROL;
			case "diesel" -> Fuel.DIESEL;
			// ponytail: only values seen live are mapped; add hybrid/LPG/electric codes once observed.
			default -> Fuel.OTHER;
		};
	}

	private static SellerType sellerType(String typename) {
		if (typename == null) {
			return SellerType.UNKNOWN;
		}
		return switch (typename) {
			case "PrivateSeller" -> SellerType.PRIVATE;
			case "ProfessionalSeller" -> SellerType.DEALER;
			default -> SellerType.UNKNOWN;
		};
	}

	static SourceException formatChanged(Throwable cause) {
		return new SourceException(FORMAT_CHANGED, cause);
	}
}
