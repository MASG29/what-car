package carfinder.source.marketplace;

import carfinder.knowledge.KnowledgeBase;
import carfinder.knowledge.Text;
import carfinder.listing.Fuel;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Reads what a Marketplace ad title says about the car. Only explicit words count; nothing is guessed. */
@Component
public class MarketplaceTitle {

	public record TitleFacts(String make, String model, Integer year, Fuel fuel) {
	}

	private static final Pattern YEAR = Pattern.compile("^\\s*((?:19|20)\\d\\d)(?!\\d)");
	private static final Pattern LPG = word("gpl");
	private static final Pattern DIESEL = word("gasoleo|diesel|tdci|tdi|hdi|dci|cdti|crdi|jtd|d-4d");
	private static final Pattern PETROL = word("gasolina");
	/** "146K km", "1.5K km", "99K miles", "12 km". */
	private static final Pattern KM = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)\\s*(k)?\\s*(km|miles)\\s*$",
			Pattern.CASE_INSENSITIVE);
	private static final double KM_PER_MILE = 1.609344;

	private final KnowledgeBase kb;

	public MarketplaceTitle(KnowledgeBase kb) {
		this.kb = kb;
	}

	/** Letters may not touch the word, digits may ("1.4tdci"). */
	private static Pattern word(String alternatives) {
		return Pattern.compile("(?<![a-z])(?:" + alternatives + ")(?![a-z])");
	}

	public TitleFacts read(String title) {
		String t = title == null ? "" : title;
		var g = kb.recogniseMakeModel(t);
		Matcher y = YEAR.matcher(t);
		return new TitleFacts(g.map(x -> x.make()).orElse(null), g.map(x -> x.model()).orElse(null),
				y.find() ? Integer.valueOf(y.group(1)) : null, fuel(Text.normalise(t)));
	}

	private static Fuel fuel(String t) {
		if (LPG.matcher(t).find()) {
			return Fuel.LPG;
		}
		if (DIESEL.matcher(t).find()) {
			return Fuel.DIESEL;
		}
		return PETROL.matcher(t).find() ? Fuel.PETROL : null;
	}

	/** Kilometres from a feed subtitle; K = thousand, miles converted and rounded; anything else is null. */
	public static Integer km(String subtitle) {
		if (subtitle == null) {
			return null;
		}
		Matcher m = KM.matcher(subtitle);
		if (!m.matches()) {
			return null;
		}
		double v = Double.parseDouble(m.group(1)) * (m.group(2) == null ? 1 : 1000)
				* (m.group(3).equalsIgnoreCase("miles") ? KM_PER_MILE : 1);
		return (int) Math.round(v);
	}
}
