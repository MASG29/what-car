package carfinder.scoring;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One PT sentence on why an ad sits where it does: strongest criteria, missing data, confidence, condition hints. */
public final class WhyText {

	private static final Map<Criterion, String> NAMES = Map.of(Criterion.PRICE, "preço", Criterion.RELIABILITY,
			"fiabilidade", Criterion.CONSUMPTION, "consumo", Criterion.COMPLETENESS, "anúncio completo",
			Criterion.PHOTOS, "fotos");
	private static final Locale PT = Locale.forLanguageTag("pt-PT");

	private WhyText() {
	}

	public static String build(ScoredListing s, int position) {
		List<String> parts = new ArrayList<>();
		List<Criterion> present = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		for (Criterion c : Criterion.values()) {
			SubScore sub = s.subs().get(c);
			if (sub != null && sub.value().isPresent()) {
				present.add(c);
			} else {
				missing.add(NAMES.get(c));
			}
		}
		// stable sort: ties keep the criterion order
		present.sort(Comparator.comparingDouble((Criterion c) -> s.subs().get(c).value().getAsDouble()).reversed());
		List<String> strongest = present.stream().limit(2)
				.map(c -> NAMES.get(c) + " " + pct(s.subs().get(c).value().getAsDouble())).toList();
		parts.add(position + ".º lugar: "
				+ (strongest.isEmpty() ? "nenhum critério com dados" : String.join(" e ", strongest)));
		if (!missing.isEmpty()) {
			parts.add(String.join(", ", missing) + " sem dados");
		}
		parts.add("confiança " + pct(s.confidence()));
		String condition = condition(s.condition());
		if (condition != null) {
			parts.add(condition);
		}
		return String.join("; ", parts) + ".";
	}

	private static String condition(ConditionResult c) {
		if (c.problemsPt().isEmpty() && c.maintenancePt().isEmpty()) {
			return null;
		}
		double adj = c.rankAdjustment();
		List<String> why = new ArrayList<>();
		if (!c.problemsPt().isEmpty()) {
			why.add(String.join(", ", c.problemsPt()) + " mencionados");
		}
		if (!c.maintenancePt().isEmpty()) {
			why.add("manutenção recente (" + String.join(", ", c.maintenancePt()) + ")");
		}
		return (adj < 0 ? "−" : "+") + String.format(PT, "%.2f", Math.abs(adj)) + " por " + String.join(" e ", why);
	}

	private static String pct(double x) {
		return Math.round(x * 100) + " %";
	}
}
