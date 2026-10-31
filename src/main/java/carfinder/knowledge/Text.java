package carfinder.knowledge;

import java.text.Normalizer;

public final class Text {

	private Text() {
	}

	/** Lower-case, no diacritics, trimmed, single spaces. */
	public static String normalise(String s) {
		return Normalizer.normalize(s, Normalizer.Form.NFD)
				.replaceAll("\\p{M}+", "")
				.toLowerCase()
				.trim()
				.replaceAll("\\s+", " ");
	}
}
