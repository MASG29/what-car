package carfinder.source.marketplace;

import static carfinder.source.Figures.kms;
import static carfinder.source.Figures.one;
import static carfinder.source.marketplace.MarketplaceFeedParser.price;
import static carfinder.source.marketplace.MarketplaceFeedParser.text;

import carfinder.geo.Places;
import carfinder.knowledge.Text;
import carfinder.listing.Fuel;
import carfinder.listing.Listing;
import carfinder.listing.RawListing;
import carfinder.listing.Site;
import carfinder.source.SoldOrReservedException;
import carfinder.source.marketplace.MarketplaceTitle.TitleFacts;
import java.time.Year;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads a logged-out Marketplace item page from its embedded JSON.
 *
 * <p>Shape (checked 2026-11-09): {@code application/json} scripts hold objects with a
 * {@code marketplace_product_details_page.target} for the item. One block has {@code marketplace_listing_title},
 * {@code redacted_description.text}, {@code listing_price.amount}, {@code is_sold}, {@code is_pending} and
 * {@code location_text.text} ("City, District"); another has {@code listing_photos[]}. The {@code vehicle_*} fields
 * were only seen null, so they are not read; year, km, fuel and gearbox come from the title, then the description.
 * Only the keys named here are read: the seller, phone and story objects are never touched.
 */
@Component
public class MarketplaceAdvertParser {

	private static final String PAGE = "marketplace_product_details_page";
	private static final Pattern JSON_SCRIPT = Pattern
			.compile("<script type=\"application/json\"[^>]*>(.*?)</script>", Pattern.DOTALL);
	private static final Pattern ITEM_ID = Pattern.compile("/marketplace/item/(\\d+)/?$");
	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	// Description rules work on Text.normalise output (lower case, no accents). Only labelled or unambiguous shapes.
	private static final Pattern YEAR_LABELLED = Pattern
			.compile("(?<![a-z])(?<!ate )(?<!ate [a-z]{3,9} )(?:ano|de|registo|matricula|matriculado em)\\s*:?\\s*"
					+ "((?:19|20)\\d\\d)(?!\\d)");
	private static final Pattern YEAR_MONTH = Pattern
			.compile("(?<![\\d/])(?:0?[1-9]|1[0-2])\\s*/\\s*((?:19|20)\\d\\d)(?![\\d/])");
	private static final Pattern GEARBOX = Pattern.compile(
			"caixa (?:de velocidades )?(manual|automatica)|(?<![a-z])(manual|automatica) de [4-9] velocidades");

	private final MarketplaceTitle titles;
	private final Places places;

	public MarketplaceAdvertParser(MarketplaceTitle titles, Places places) {
		this.titles = titles;
		this.places = places;
	}

	/** Throws {@link LoginWallException} when the page holds no data for the item. */
	public RawListing parse(String html, String url) {
		Matcher idMatch = ITEM_ID.matcher(url);
		if (!idMatch.find()) {
			throw new IllegalArgumentException("Not a Marketplace item URL: " + url);
		}
		String id = idMatch.group(1);
		JsonNode details = null;
		int photos = 0;
		Matcher m = JSON_SCRIPT.matcher(html);
		while (m.find()) {
			if (!m.group(1).contains(PAGE)) {
				continue;
			}
			JsonNode root;
			try {
				root = MAPPER.readTree(m.group(1));
			}
			catch (JacksonException e) {
				continue; // not ours; its content is never logged
			}
			for (JsonNode target : targets(root)) {
				if (!id.equals(text(target.get("id")))) {
					continue;
				}
				photos = Math.max(photos, target.path("listing_photos").size());
				if (details == null && text(target.get("marketplace_listing_title")) != null) {
					details = target;
				}
			}
		}
		if (details == null) {
			throw new LoginWallException();
		}
		if (details.path("is_sold").asBoolean(false) || details.path("is_pending").asBoolean(false)) {
			throw new SoldOrReservedException();
		}
		String title = text(details.get("marketplace_listing_title"));
		String description = description(text(details.path("redacted_description").get("text")));
		TitleFacts t = titles.read(title);
		String d = description == null ? "" : Text.normalise(description);
		Integer year = t.year() != null ? t.year() : one(years(d));
		Fuel fuel = t.fuel() != null ? t.fuel() : fuel(d);
		String[] place = location(text(details.path("location_text").get("text")));
		return new RawListing(Site.MARKETPLACE, id, "https://www.facebook.com/marketplace/item/" + id + "/", title,
				price(text(details.path("listing_price").get("amount"))), t.make(), t.model(), null, year, null,
				one(kms(d)), fuel, null, null, gearbox(d), null, null, place[1], place[0], photos, description, null,
				false);
	}

	/** Every {@code marketplace_product_details_page.target}; the walk never enters a target. */
	private static Set<JsonNode> targets(JsonNode root) {
		Set<JsonNode> found = new LinkedHashSet<>();
		Deque<JsonNode> stack = new ArrayDeque<>();
		stack.push(root);
		while (!stack.isEmpty()) {
			JsonNode n = stack.pop();
			if (n.isObject() && n.has(PAGE)) {
				JsonNode target = n.path(PAGE).path("target");
				if (target.isObject()) {
					found.add(target);
				}
				continue;
			}
			if (n.isContainer()) {
				n.values().forEach(stack::push);
			}
		}
		return found;
	}

	/** {city, district}: "Barcelos, Braga"; the district only when it names one. */
	private String[] location(String text) {
		if (text == null) {
			return new String[] {null, null};
		}
		String[] parts = text.split(",");
		String city = parts[0].strip().isEmpty() ? null : parts[0].strip();
		String district = parts.length >= 2 ? places.district(parts[1].strip()).orElse(null) : null;
		return new String[] {city, district};
	}

	private static String description(String s) {
		if (s == null) {
			return null;
		}
		if (s.indexOf(' ') < 0) {
			s = s.replace('+', ' ').strip(); // some ads arrive form-encoded: "Levou+essa+manutencao"
		}
		return s.length() > Listing.DESCRIPTION_MAX ? s.substring(0, Listing.DESCRIPTION_MAX) : s;
	}

	private static Set<Integer> years(String d) {
		Set<Integer> found = new LinkedHashSet<>();
		int max = Year.now().getValue(); // a later year is a date to come (inspection, insurance)
		for (Pattern p : new Pattern[] {YEAR_LABELLED, YEAR_MONTH}) {
			Matcher m = p.matcher(d);
			while (m.find()) {
				int y = Integer.parseInt(m.group(1));
				if (y >= 1950 && y <= max) {
					found.add(y);
				}
			}
		}
		return found;
	}

	/** GPL wins (those cars also run on petrol); diesel and petrol words together say nothing. */
	private static Fuel fuel(String d) {
		if (MarketplaceTitle.LPG.matcher(d).find()) {
			return Fuel.LPG;
		}
		boolean diesel = MarketplaceTitle.DIESEL.matcher(d).find();
		boolean petrol = MarketplaceTitle.PETROL.matcher(d).find();
		return diesel == petrol ? null : diesel ? Fuel.DIESEL : Fuel.PETROL;
	}

	private static String gearbox(String d) {
		Set<String> found = new LinkedHashSet<>();
		Matcher m = GEARBOX.matcher(d);
		while (m.find()) {
			String g = m.group(1) != null ? m.group(1) : m.group(2);
			found.add(g.equals("manual") ? "manual" : "automatic");
		}
		return one(found);
	}

}
