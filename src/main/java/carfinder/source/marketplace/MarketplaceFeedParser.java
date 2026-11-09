package carfinder.source.marketplace;

import carfinder.geo.Places;
import carfinder.listing.RawListing;
import carfinder.listing.Site;
import carfinder.source.SourceException;
import carfinder.source.marketplace.MarketplaceTitle.TitleFacts;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns Marketplace feed bodies (graphql responses, and the page document whose embedded JSON holds the first batch)
 * into listings. Only the item fields named here are read; the seller object is never touched.
 */
@Component
public class MarketplaceFeedParser {

	public static final String NO_ADS = "Este ficheiro não tem anúncios do Marketplace. Confirma os filtros Doc e Fetch/XHR.";
	static final String VEHICLES = "807311116002614";
	private static final String GRAPHQL_PREFIX = "for (;;);";
	private static final Pattern JSON_SCRIPT = Pattern
			.compile("<script type=\"application/json\"[^>]*>(.*?)</script>", Pattern.DOTALL);
	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	/** Live vehicles (one per id) and the ids seen sold or reserved. */
	public record FeedResult(List<RawListing> live, List<String> unavailableIds) {
	}

	private final MarketplaceTitle titles;
	private final Places places;

	public MarketplaceFeedParser(MarketplaceTitle titles, Places places) {
		this.titles = titles;
		this.places = places;
	}

	public FeedResult parse(Stream<String> bodies) {
		Map<String, RawListing> live = new LinkedHashMap<>();
		Set<String> unavailable = new LinkedHashSet<>();
		boolean anyBody = false;
		boolean anyVehicle = false;
		for (Iterator<String> it = bodies.iterator(); it.hasNext();) {
			String body = it.next();
			anyBody = true;
			for (String doc : documents(body)) {
				JsonNode root;
				try {
					root = MAPPER.readTree(doc);
				}
				catch (JacksonException e) {
					continue; // another kind of response; its content is never logged
				}
				anyVehicle |= collect(root, live, unavailable);
			}
		}
		if (!anyBody) {
			throw new SourceException(NO_ADS);
		}
		if (!anyVehicle) {
			throw new SourceException(Site.MARKETPLACE.formatChangedPt());
		}
		// seen sold or reserved anywhere in the file wins over a live copy of the same ad
		live.keySet().removeAll(unavailable);
		return new FeedResult(List.copyOf(live.values()), List.copyOf(unavailable));
	}

	/** graphql: one JSON document per line; HTML: each application/json script that mentions an item. */
	private static List<String> documents(String body) {
		List<String> docs = new ArrayList<>();
		if (body.stripLeading().startsWith("<")) {
			Matcher m = JSON_SCRIPT.matcher(body);
			while (m.find()) {
				if (m.group(1).contains("marketplace_listing_title")) {
					docs.add(m.group(1));
				}
			}
			return docs;
		}
		for (String line : body.split("\n")) {
			String l = line.strip();
			if (l.startsWith(GRAPHQL_PREFIX)) {
				l = l.substring(GRAPHQL_PREFIX.length());
			}
			if (!l.isEmpty()) {
				docs.add(l);
			}
		}
		return docs;
	}

	/** Walks the document for items (objects with id + marketplace_listing_title); true if a vehicle was found. */
	private boolean collect(JsonNode root, Map<String, RawListing> live, Set<String> unavailable) {
		boolean found = false;
		Deque<JsonNode> stack = new ArrayDeque<>();
		stack.push(root);
		while (!stack.isEmpty()) {
			JsonNode n = stack.pop();
			if (n.isObject() && n.has("id") && n.has("marketplace_listing_title")) {
				if (!VEHICLES.equals(text(n.get("marketplace_listing_category_id")))) {
					continue;
				}
				found = true;
				String id = text(n.get("id"));
				if (id == null) {
					continue;
				}
				if (n.path("is_sold").asBoolean(false) || n.path("is_pending").asBoolean(false)) {
					unavailable.add(id);
				} else {
					live.putIfAbsent(id, listing(id, n));
				}
				continue; // never walk into an item (its seller object stays unread)
			}
			if (n.isContainer()) {
				n.values().forEach(stack::push);
			}
		}
		return found;
	}

	private RawListing listing(String id, JsonNode n) {
		// the seller's title keeps engine and condition words; custom_title is Facebook's short "year make model"
		String seller = text(n.get("marketplace_listing_title"));
		String custom = text(n.get("custom_title"));
		String title = seller != null ? seller : custom;
		if (title == null) {
			title = "";
		}
		Integer km = null;
		for (JsonNode s : n.path("custom_sub_titles_with_rendering_flags")) {
			km = MarketplaceTitle.km(text(s.get("subtitle")));
			if (km != null) {
				break;
			}
		}
		JsonNode geo = n.path("location").path("reverse_geocode");
		TitleFacts f = facts(title, custom);
		return new RawListing(Site.MARKETPLACE, id, "https://www.facebook.com/marketplace/item/" + id + "/", title,
				price(text(n.path("listing_price").get("amount"))), f.make(), f.model(), null, f.year(), null, km,
				f.fuel(), null, null, null, null, null, district(text(geo.path("city_page").get("display_name"))),
				text(geo.get("city")), 0, null, null, false);
	}

	/**
	 * The seller's words first; custom_title (the year/make/model picked in Facebook's form) fills the car when the
	 * title names none, and the year when both name the same car.
	 */
	private TitleFacts facts(String title, String custom) {
		TitleFacts f = titles.read(title);
		if (custom == null || custom.equals(title)) {
			return f;
		}
		TitleFacts c = titles.read(custom);
		if (f.make() == null) {
			return new TitleFacts(c.make(), c.model(), f.year() != null ? f.year() : c.year(), f.fuel());
		}
		boolean sameCar = f.make().equals(c.make()) && f.model().equals(c.model());
		return f.year() == null && sameCar ? new TitleFacts(f.make(), f.model(), c.year(), f.fuel()) : f;
	}

	/** "Paramos, Aveiro, Portugal" names a district in the middle; "Leça, Matosinhos, Portugal" does not. */
	private String district(String displayName) {
		if (displayName == null) {
			return null;
		}
		String[] parts = displayName.split(",");
		return parts.length == 3 ? places.district(parts[1].strip()).orElse(null) : null;
	}

	private static Integer price(String amount) {
		if (amount == null) {
			return null;
		}
		try {
			double v = Double.parseDouble(amount);
			return v < 0 || v > Integer.MAX_VALUE ? null : (int) Math.round(v);
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	/** A non-blank scalar as text, else null. */
	private static String text(JsonNode n) {
		if (n == null || n.isNull() || n.isContainer()) {
			return null;
		}
		String s = n.asString().strip();
		return s.isEmpty() ? null : s;
	}
}
