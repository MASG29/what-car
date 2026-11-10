package carfinder.source.standvirtual;

import carfinder.knowledge.Text;
import java.net.URI;

public final class StandvirtualUrls {

	private StandvirtualUrls() {
	}

	/** Newest-first search capped at maxPrice; order verified against the live site on 2026-10-31. */
	public static URI search(URI base, int maxPrice, int page) {
		return search(base, maxPrice, page, null, null);
	}

	/**
	 * With make and model, the path is /carros/<make>/<model> (worked on 2026-10-25; the site knows Volkswagen as "vw").
	 * A slug the site does not know drops both filters there, which only widens the search: the app still filters.
	 */
	public static URI search(URI base, int maxPrice, int page, String make, String model) {
		String path = make == null || model == null ? "/carros" : "/carros/" + slug(make) + "/" + slug(model);
		// Brackets and colons are percent-encoded: java.net.URI rejects them raw in a query.
		return URI.create(base.toString().replaceAll("/+$", "") + path + "?search%5Bfilter_float_price%3Ato%5D=" + maxPrice
				+ "&search%5Border%5D=created_at_first%3Adesc&page=" + page);
	}

	private static String slug(String name) {
		String s = Text.normalise(name).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
		return s.equals("volkswagen") ? "vw" : s;
	}
}
