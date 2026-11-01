package carfinder.knowledge;

import java.text.Normalizer;
import java.util.Locale;

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

	/** PT integer: no-break space between thousands, only from 10 000 ("9500", "169 000"). */
	public static String thousandsPt(long n) {
		return Math.abs(n) < 10_000 ? Long.toString(n) : String.format(Locale.ROOT, "%,d", n).replace(',', '\u00A0');
	}
}
