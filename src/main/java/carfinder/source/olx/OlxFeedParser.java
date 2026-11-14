package carfinder.source.olx;

import carfinder.geo.Places;
import carfinder.knowledge.Generation;
import carfinder.knowledge.KnowledgeBase;
import carfinder.knowledge.Text;
import carfinder.listing.Fuel;
import carfinder.listing.Listing;
import carfinder.listing.RawListing;
import carfinder.listing.SellerType;
import carfinder.listing.Site;
import carfinder.scoring.ConditionReader;
import carfinder.source.Figures;
import carfinder.source.SourceException;
import carfinder.source.marketplace.HarReader;
import carfinder.source.marketplace.MarketplaceFeedParser.FeedResult;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns the bodies of an OLX HAR into listings. Two shapes, checked on 2026-11-14:
 * <ul>
 * <li>graphql responses: {@code data.clientCompatibleListings.data[]}, snake_case, param values in
 * {@code value.key}/{@code value.label}, price in the {@code price} param's {@code value.value};</li>
 * <li>the search page: {@code window.__PRERENDERED_STATE__ = "<JSON in a string>"} with {@code listing.listing.ads[]},
 * camelCase, param values in {@code normalizedValue}/{@code value}, price in {@code price.regularPrice.value}. The same
 * state holds {@code categories.list}, the only map from an ad's category id to its make.</li>
 * </ul>
 * Only the fields named here are read. The seller ({@code user}, {@code contact}, {@code shop}), the map position and
 * the plate/VIN params are never touched. Ads OLX copies from Standvirtual ({@code partner.code}) are skipped: the
 * Standvirtual source reads them in full (Marco, 2026-11-14).
 */
@Component
public class OlxFeedParser {

	public static final String NO_ADS = "Este ficheiro não tem anúncios do OLX. Confirma os filtros Doc e Fetch/XHR.";
	public static final HarReader.Filter HAR = new HarReader.Filter("https://www.olx.pt/apigateway/graphql",
			"https://www.olx.pt/carros-motos-e-barcos/", NO_ADS);

	private static final String HOST = "https://www.olx.pt/";
	private static final String STATE = "window.__PRERENDERED_STATE__";
	private static final Pattern BREAK = Pattern.compile("<br\\s*/?>", Pattern.CASE_INSENSITIVE);
	private static final Pattern TAG = Pattern.compile("<[^>]*>");
	private static final Pattern LAST_NUMBER = Pattern.compile("(\\d+)\\D*$");
	/** The only params read; plate ("matricula") and VIN never are. */
	private static final Set<String> PARAMS = Set.of("price", "modelo", "year", "quilometros", "combustivel", "gearbox",
			"engine_capacity", "engine_power", "portas", "first_registration_month");
	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private final KnowledgeBase kb;
	private final Places places;

	public OlxFeedParser(KnowledgeBase kb, Places places) {
		this.kb = kb;
		this.places = places;
	}

	public FeedResult parse(Stream<String> bodies) {
		List<JsonNode> items = new ArrayList<>();
		Map<String, String> makes = new HashMap<>();
		boolean anyList = false;
		for (Iterator<String> it = bodies.iterator(); it.hasNext();) {
			String body = it.next();
			JsonNode ads;
			if (body.stripLeading().startsWith("<")) {
				JsonNode state = state(body);
				ads = state.path("listing").path("listing").path("ads");
				state.path("categories").path("list").values().forEach(c -> {
					String id = text(c.get("id"));
					String name = text(c.get("name"));
					if (id != null && name != null) {
						makes.put(id, name);
					}
				});
			} else {
				ads = tree(body).path("data").path("clientCompatibleListings").path("data");
			}
			if (ads.isArray()) {
				anyList = true;
				ads.forEach(items::add);
			}
		}
		if (!anyList) {
			throw new SourceException(NO_ADS);
		}
		// the category map may come after the graphql bodies, so items are mapped once every body is read
		Map<String, RawListing> live = new LinkedHashMap<>();
		Set<String> unavailable = new LinkedHashSet<>();
		Set<String> mirrors = new LinkedHashSet<>();
		for (JsonNode n : items) {
			String id = text(n.get("id"));
			if (id == null) {
				throw formatChanged();
			}
			if ("standvirtual".equals(text(n.path("partner").get("code")))) {
				mirrors.add(id);
			} else if (!"active".equals(text(n.get("status")))) {
				unavailable.add(id);
			} else {
				live.putIfAbsent(id, listing(id, n, makes));
			}
		}
		// seen gone anywhere in the file wins over a live copy of the same ad
		live.keySet().removeAll(unavailable);
		return new FeedResult(List.copyOf(live.values()), List.copyOf(unavailable), mirrors.size());
	}

	private RawListing listing(String id, JsonNode n, Map<String, String> makes) {
		String title = text(n.get("title"));
		String url = text(n.get("url"));
		if (title == null || url == null || !url.startsWith(HOST)) {
			throw formatChanged();
		}
		Map<String, String> p = new HashMap<>();
		String modelLabel = null;
		for (JsonNode param : n.path("params")) {
			String key = text(param.get("key"));
			if (key == null || !PARAMS.contains(key)) {
				continue;
			}
			JsonNode v = param.get("value");
			if (v != null && v.isObject()) { // graphql
				p.put(key, text(key.equals("price") ? v.get("value") : v.get("key")));
				if (key.equals("modelo")) {
					modelLabel = text(v.get("label"));
				}
			} else { // page state
				p.put(key, text(param.get("normalizedValue")));
				if (key.equals("modelo")) {
					modelLabel = text(v);
				}
			}
		}
		String description = description(text(n.get("description")));
		Integer km = number(p.get("quilometros"));
		if (km == null && description != null) {
			km = Figures.one(Figures.kms(Text.normalise(description)));
		}
		Integer price = euros(p.get("price"));
		if (price == null) {
			price = euros(text(n.path("price").path("regularPrice").get("value")));
		}
		Optional<Generation> g = makeModel(title, makes.get(text(n.path("category").get("id"))), modelLabel);
		JsonNode where = n.path("location");
		String city = Optional.ofNullable(text(where.path("city").get("name"))).orElse(text(where.get("cityName")));
		String region = Optional.ofNullable(text(where.path("region").get("name")))
				.orElse(text(where.get("regionName")));
		JsonNode business = n.has("business") ? n.get("business") : n.get("isBusiness");
		return new RawListing(Site.OLX, id, url, title, price, g.map(Generation::make).orElse(null),
				g.map(Generation::model).orElse(null), null, number(p.get("year")),
				number(p.get("first_registration_month")), km, fuel(p.get("combustivel")),
				number(p.get("engine_capacity")), number(p.get("engine_power")), p.get("gearbox") /* "manual"/"automatic" are the app's values */,
				doors(p.get("portas")), seller(business),
				region == null ? null : places.district(region).orElse(null), city, n.path("photos").size(),
				description, null, ConditionReader.read(title, description).parts());
	}

	/**
	 * The site's own make (category) and model fields first; without the make (no page document in the HAR), a model
	 * that only one make has; the title when they name no car the app knows.
	 */
	private Optional<Generation> makeModel(String title, String make, String model) {
		Optional<Generation> fields = model == null ? Optional.empty()
				: make != null ? kb.recogniseMakeModel(make + " " + model) : kb.recogniseTyped(model);
		return fields.isPresent() ? fields : kb.recogniseMakeModel(title);
	}

	/** The JSON held in the page's {@code __PRERENDERED_STATE__} string; a missing object when the page has none. */
	private static JsonNode state(String html) {
		int at = html.indexOf(STATE);
		int eq = at < 0 ? -1 : html.indexOf('=', at + STATE.length());
		if (eq < 0) {
			return MAPPER.missingNode();
		}
		try (StringReader r = new StringReader(html)) {
			r.skip(eq + 1);
			try (JsonParser p = MAPPER.createParser(r)) {
				return p.nextToken() == JsonToken.VALUE_STRING ? tree(p.getString()) : MAPPER.missingNode();
			}
		}
		catch (IOException | JacksonException e) {
			return MAPPER.missingNode(); // its content is never logged
		}
	}

	private static JsonNode tree(String json) {
		try {
			return MAPPER.readTree(json);
		}
		catch (JacksonException e) {
			return MAPPER.missingNode(); // another kind of response; its content is never logged
		}
	}

	/** OLX keeps line breaks as {@code <br />}; nothing else in the text is markup. */
	static String description(String html) {
		if (html == null) {
			return null;
		}
		String s = TAG.matcher(BREAK.matcher(html).replaceAll("\n")).replaceAll("").strip();
		return s.isEmpty() ? null : s.substring(0, Math.min(s.length(), Listing.DESCRIPTION_MAX));
	}

	/** The site's fuel keys (2026-11-14). */
	static Fuel fuel(String key) {
		if (key == null) {
			return null;
		}
		return switch (key) {
			case "gasolina" -> Fuel.PETROL;
			case "diesel" -> Fuel.DIESEL;
			case "gpl" -> Fuel.LPG;
			case "hibrido", "plugin-hybrid" -> Fuel.HYBRID;
			case "electrico" -> Fuel.ELECTRIC;
			default -> Fuel.OTHER;
		};
	}

	/** "1-3" → 3, "4-5" → 5: the upper end of the site's band. */
	static Integer doors(String key) {
		if (key == null) {
			return null;
		}
		Matcher m = LAST_NUMBER.matcher(key);
		return m.find() ? number(m.group(1)) : null;
	}

	static SellerType seller(JsonNode business) {
		return business != null && business.isBoolean()
				? (business.asBoolean() ? SellerType.DEALER : SellerType.PRIVATE)
				: SellerType.UNKNOWN;
	}

	/** Whole euros from "1290" or "1290.5"; null otherwise. */
	static Integer euros(String s) {
		return s == null ? null : number(s.replaceFirst("\\.\\d*$", ""));
	}

	/** Digits only, otherwise null; never a default 0. */
	static Integer number(String s) {
		String d = s == null ? "" : s.replaceAll("\\D", "");
		return d.isEmpty() || d.length() > 9 ? null : Integer.valueOf(d);
	}

	static String text(JsonNode n) {
		if (n == null || n.isNull() || n.isContainer() || n.isMissingNode()) {
			return null;
		}
		String s = n.asString().strip();
		return s.isEmpty() ? null : s;
	}

	private static SourceException formatChanged() {
		return new SourceException(Site.OLX.formatChangedPt());
	}
}
