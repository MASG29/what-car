package carfinder.source.standvirtual;

import static carfinder.source.standvirtual.StandvirtualParser.digits;
import static carfinder.source.standvirtual.StandvirtualParser.formatChanged;
import static carfinder.source.standvirtual.StandvirtualParser.fuel;
import static carfinder.source.standvirtual.StandvirtualParser.nextData;
import static carfinder.source.standvirtual.StandvirtualParser.text;

import carfinder.listing.RawListing;
import carfinder.listing.SellerType;
import carfinder.listing.Site;
import tools.jackson.databind.JsonNode;

/**
 * Reads a Standvirtual advert page from its embedded {@code __NEXT_DATA__} JSON.
 *
 * <p>Path (checked 2026-10-31): {@code props.pageProps.advert} with {@code id}, {@code title},
 * {@code url}, {@code price.value}, {@code images.photos[]}, {@code description} (HTML with
 * {@code <br>}), {@code seller.type} ("PROFESSIONAL"), {@code seller.location.city/region},
 * {@code isParts} and {@code parametersDict.<key>.values[0]} as {@code {value, label}}. The label
 * matches the search page's displayValue. {@code is_imported_car} is "0"/"1".
 */
public class StandvirtualAdvertParser {

	private static final int DESCRIPTION_MAX = 4000;

	public RawListing parse(String html, String url) {
		JsonNode advert = nextData(html).path("props").path("pageProps").path("advert");
		String id = text(advert.path("id"));
		String title = text(advert.path("title"));
		JsonNode params = advert.path("parametersDict");
		if (id == null || title == null || !params.isObject()) {
			throw formatChanged(null);
		}
		String advertUrl = text(advert.path("url"));
		JsonNode location = advert.path("seller").path("location");
		return new RawListing(Site.STANDVIRTUAL, id, advertUrl != null ? advertUrl : url, title,
				digits(advert.path("price").path("value")),
				text(first(params, "make").path("label")),
				text(first(params, "model").path("label")),
				text(first(params, "version").path("label")),
				digits(value(params, "first_registration_year")),
				digits(value(params, "first_registration_month")),
				digits(value(params, "mileage")),
				fuel(text(value(params, "fuel_type"))),
				digits(value(params, "engine_capacity")),
				digits(value(params, "engine_power")),
				text(value(params, "gearbox")),
				digits(value(params, "door_count")),
				sellerType(text(advert.path("seller").path("type"))),
				text(location.path("region")),
				text(location.path("city")),
				advert.path("images").path("photos").size(),
				description(text(advert.path("description"))),
				imported(text(value(params, "is_imported_car"))),
				advert.path("isParts").asBoolean(false));
	}

	private static JsonNode first(JsonNode params, String key) {
		return params.path(key).path("values").path(0);
	}

	private static JsonNode value(JsonNode params, String key) {
		return first(params, key).path("value");
	}

	/** Line breaks kept, other tags dropped, cut to the DB column size. */
	private static String description(String html) {
		if (html == null) {
			return null;
		}
		String s = html.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]*>", "").strip();
		if (s.isEmpty()) {
			return null;
		}
		return s.length() > DESCRIPTION_MAX ? s.substring(0, DESCRIPTION_MAX) : s;
	}

	private static Boolean imported(String value) {
		if (value == null) {
			return null;
		}
		return switch (value) {
			case "1" -> true;
			case "0" -> false;
			default -> null;
		};
	}

	/** Null when unknown, so it never overwrites the seller type a search page already stored. */
	private static SellerType sellerType(String type) {
		// Limitation: only "PROFESSIONAL" seen live; map the private-seller code once observed.
		return "PROFESSIONAL".equals(type) ? SellerType.DEALER : null;
	}
}
