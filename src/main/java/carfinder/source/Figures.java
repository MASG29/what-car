package carfinder.source;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Figures read from ad text that is already {@code Text.normalise}d (lower case, no accents). */
public final class Figures {

	private static final String SEP = "[ .\\u00a0]";
	private static final Pattern KM = Pattern.compile(
			"(?<![\\d.,])(\\d{1,3}(?:" + SEP + "\\d{3})+|\\d{4,7})\\s*(?:kms?|quilometros)(?![a-z])");
	private static final Pattern KM_THOUSANDS = Pattern
			.compile("(?<![\\d.,])(\\d{1,3})\\s*mil\\s*(?:kms?|quilometros)(?![a-z])");

	private Figures() {
	}

	/** Every odometer-looking figure in normalised text ("216.000 km", "216 mil km"). */
	public static Set<Integer> kms(String d) {
		Set<Integer> found = new LinkedHashSet<>();
		Matcher m = KM.matcher(d);
		while (m.find()) {
			found.add(Integer.parseInt(m.group(1).replaceAll("\\D", "")));
		}
		Matcher k = KM_THOUSANDS.matcher(d);
		while (k.find()) {
			found.add(Integer.parseInt(k.group(1)) * 1000);
		}
		return found;
	}

	/** The value when the text gives exactly one, else null (two different figures are never guessed between). */
	public static <T> T one(Set<T> values) {
		return values.size() == 1 ? values.iterator().next() : null;
	}
}
