package carfinder.source.custojusto;

import static carfinder.source.custojusto.CustoJustoParser.formatChanged;
import static carfinder.source.custojusto.CustoJustoParser.fuel;
import static carfinder.source.custojusto.CustoJustoParser.gearbox;
import static carfinder.source.custojusto.CustoJustoParser.nextData;
import static carfinder.source.custojusto.CustoJustoParser.number;
import static carfinder.source.custojusto.CustoJustoParser.seller;
import static carfinder.source.custojusto.CustoJustoParser.text;

import carfinder.geo.Places;
import carfinder.knowledge.Generation;
import carfinder.knowledge.KnowledgeBase;
import carfinder.knowledge.Text;
import carfinder.listing.RawListing;
import carfinder.listing.Site;
import carfinder.source.Figures;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Reads a CustoJusto advert page from its embedded {@code __NEXT_DATA__} JSON.
 *
 * <p>Shape (checked 2026-11-12): {@code props.pageProps.adData} has {@code listID}, {@code title}, {@code body}
 * (HTML with {@code <br>}), {@code price}, {@code companyAd}, {@code url}, {@code extraImages[]} (with a
 * {@code deleted} flag) and {@code params.capacity/power/regdate/regmonth}; {@code props.pageProps.base} has
 * {@code brand.name}, {@code model.name}, {@code fuel.name}, {@code gearbox.name}, {@code district.name},
 * {@code county.name} and {@code mileage}, a band ("200 000 - 249 999"). The site has no exact km: one figure from
 * the description is used when it falls inside that band. The seller's name, id and map position are never read.
 */
@Component
public class CustoJustoAdvertParser {

	private static final int DESCRIPTION_MAX = 4000;

	private final KnowledgeBase kb;
	private final Places places;

	public CustoJustoAdvertParser(KnowledgeBase kb, Places places) {
		this.kb = kb;
		this.places = places;
	}

	public RawListing parse(String html, String url) {
		JsonNode props = nextData(html).path("props").path("pageProps");
		JsonNode ad = props.path("adData");
		JsonNode base = props.path("base");
		String id = text(ad.path("listID"));
		String title = text(ad.path("title"));
		if (id == null || title == null || !base.isObject()) {
			throw formatChanged(null);
		}
		String brand = text(base.path("brand").path("name"));
		String model = text(base.path("model").path("name"));
		// the site's labels ("VW", "Classe C") go through the knowledge base, else the title; else kept as given
		Optional<Generation> g = kb.recogniseMakeModel((brand == null ? "" : brand) + " " + (model == null ? "" : model))
				.or(() -> kb.recogniseMakeModel(title));
		String description = description(text(ad.path("body")));
		JsonNode params = ad.path("params");
		String path = text(ad.path("url"));
		return new RawListing(Site.CUSTOJUSTO, id, path != null ? CustoJustoParser.HOST + path : url, title,
				number(ad.path("price")), g.map(Generation::make).orElse(brand),
				g.map(Generation::model).orElse(model), null, number(params.path("regdate")),
				number(params.path("regmonth")), km(description, text(base.path("mileage"))),
				fuel(text(base.path("fuel").path("name"))), number(params.path("capacity")),
				number(params.path("power")), gearbox(text(base.path("gearbox").path("name"))), null,
				seller(ad.path("companyAd")), places.district(text(base.path("district").path("name"))).orElse(null),
				text(base.path("county").path("name")), photos(ad.path("extraImages")), description, null, false);
	}

	/** The one km figure in the description, if it lies inside the site's band ("200 000 - 249 999"). */
	private static Integer km(String description, String band) {
		if (description == null) {
			return null;
		}
		Integer km = Figures.one(Figures.kms(Text.normalise(description)));
		if (km == null || band == null) {
			return km;
		}
		String[] ends = band.replaceAll("[^\\d-]", "").split("-");
		if (ends.length != 2 || ends[0].isEmpty() || ends[1].isEmpty()) {
			return km;
		}
		return km >= Long.parseLong(ends[0]) && km <= Long.parseLong(ends[1]) ? km : null;
	}

	private static int photos(JsonNode images) {
		return (int) StreamSupport.stream(images.spliterator(), false)
				.filter(i -> !i.path("deleted").asBoolean(false)).count();
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
}
