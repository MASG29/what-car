package carfinder.source.standvirtual;

import java.net.URI;

public final class StandvirtualUrls {

	private StandvirtualUrls() {
	}

	/** Newest-first search capped at maxPrice; order verified against the live site on 2026-10-31. */
	public static URI search(int maxPrice, int page) {
		return search(URI.create("https://www.standvirtual.com"), maxPrice, page);
	}

	public static URI search(URI base, int maxPrice, int page) {
		// Brackets and colons are percent-encoded: java.net.URI rejects them raw in a query.
		return URI.create(base.toString().replaceAll("/+$", "") + "/carros?search%5Bfilter_float_price%3Ato%5D=" + maxPrice
				+ "&search%5Border%5D=created_at_first%3Adesc&page=" + page);
	}
}
