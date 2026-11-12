package carfinder.source.custojusto;

import carfinder.knowledge.Text;
import java.net.URI;

public final class CustoJustoUrls {

	static final String CARS = "/portugal/veiculos/carros-usados";
	/** Below this the app treats a price as symbolic; most are dealers' 0 EUR ads (a third of a run, 2026-11-12). */
	private static final int MIN_PRICE = 100;

	private CustoJustoUrls() {
	}

	/**
	 * Cars in all of Portugal, newest first (the site's default order, checked 2026-11-12), price floor {@code ps} and
	 * cap {@code pe}, minimum registration year {@code rs}, page {@code o} from 2. With make and model the path is
	 * /&lt;make&gt;/&lt;model&gt; (the site knows Volkswagen as "vw"); km bands are list positions, so km is filtered
	 * locally.
	 */
	public static URI search(URI base, int maxPrice, Integer minYear, int page, String make, String model) {
		String path = make == null || model == null ? CARS : CARS + "/" + slug(make) + "/" + slug(model);
		return URI.create(base.toString().replaceAll("/+$", "") + path + "?ps=" + MIN_PRICE + "&pe=" + maxPrice
				+ (minYear == null ? "" : "&rs=" + minYear) + (page > 1 ? "&o=" + page : ""));
	}

	private static String slug(String name) {
		String s = Text.normalise(name).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
		return s.equals("volkswagen") ? "vw" : s;
	}
}
