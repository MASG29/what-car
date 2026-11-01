package carfinder.scoring;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;

import carfinder.knowledge.Cause;
import carfinder.knowledge.Generation;
import carfinder.knowledge.KnowledgeBase;
import carfinder.knowledge.Survey;
import carfinder.knowledge.SurveyResult;
import carfinder.knowledge.Tier;
import carfinder.listing.Listing;
import carfinder.matching.CarMatch;
import carfinder.matching.FaultHit;
import carfinder.matching.GearboxHit;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.OptionalDouble;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Reliability sub-score from the first fallback step that yields rows: same generation at the car's age band,
 * same generation at the oldest band, model-series rows at the nearest registration year, engine faults only,
 * nothing. Rows of another generation are never used.
 */
@Component
public class ReliabilityScorer {

	static final String BATTERY_WARNING = "Verifica a bateria de 12 V";
	static final String VIN_WARNING = "Pede o VIN ou o código do motor para confirmar";
	private static final Pattern BAND = Pattern.compile("_(\\d+)_(\\d+)y$");

	private final KnowledgeBase kb;

	public ReliabilityScorer(KnowledgeBase kb) {
		this.kb = kb;
	}

	public SubScore score(CarMatch m, Listing l, int carAgeYears) {
		List<SurveyResult> genRows = m.generation().map(g -> kb.surveysForGeneration(g.id())).orElse(List.of());
		List<SurveyResult> seriesRows = seriesRows(m);
		List<SurveyResult> battery = Stream.concat(genRows.stream(), seriesRows.stream())
				.filter(s -> s.cause() == Cause.BATTERY).toList();
		List<SurveyResult> gen = genRows.stream().filter(ReliabilityScorer::usable).toList();
		List<SurveyResult> series = seriesRows.stream().filter(ReliabilityScorer::usable).toList();

		List<SurveyResult> rows;
		double base;
		String text;
		List<SurveyResult> band = gen.stream().filter(s -> covers(s, carAgeYears)).toList();
		if (!band.isEmpty()) {
			rows = band;
			base = 1.0;
			text = label(rows, SurveyResult::edition) + ", aos ~" + ages(rows) + " anos";
		} else if (!gen.isEmpty()) {
			int oldest = gen.stream().mapToInt(SurveyResult::ageYears).max().orElseThrow();
			rows = gen.stream().filter(s -> s.ageYears() == oldest).toList();
			base = 0.7;
			text = "último dado: " + label(rows, SurveyResult::edition) + ", aos ~" + oldest + " anos";
		} else if (!series.isEmpty() && l.getYear() != null) {
			int nearest = series.stream().mapToInt(s -> Math.abs(s.regYear() - l.getYear())).min().orElseThrow();
			rows = series.stream().filter(s -> Math.abs(s.regYear() - l.getYear()) == nearest).toList();
			base = 0.5;
			text = "aproximado: " + rows.getFirst().make() + " " + rows.getFirst().model() + " "
					+ label(rows, s -> "EZ" + s.regYear()) + ", aos ~" + ages(rows) + " anos";
		} else {
			rows = List.of();
			base = m.faults().isEmpty() ? 0.0 : 0.2;
			text = m.faults().isEmpty() ? "sem dados"
					: "sem dados de inquéritos; avarias conhecidas do motor: "
							+ m.faults().stream().map(h -> h.fault().fault()).collect(joining("; "));
		}
		if (rows.stream().anyMatch(SurveyResult::combined)) {
			text += " (linha combinada)";
		}

		List<String> evidenceTags = rows.isEmpty() ? m.faults().stream().map(h -> h.fault().tag()).toList()
				: rows.stream().map(SurveyResult::tag).toList();
		double confidence = base * evidenceTags.stream().mapToDouble(ReliabilityScorer::tagFactor).min().orElse(1.0)
				* (m.mixed() ? 0.8 : 1.0);

		OptionalDouble value = OptionalDouble.empty();
		if (!rows.isEmpty()) {
			double mean = rows.stream().mapToDouble(ReliabilityScorer::rowScore).average().orElseThrow();
			double penalty = Stream.concat(m.faults().stream().map(h -> h.fault().tier()),
					m.gearbox().stream().map(h -> h.rule().tier())).mapToDouble(ReliabilityScorer::penalty).sum();
			value = OptionalDouble.of(Math.max(0.0, mean - penalty));
		}

		List<String> warnings = new ArrayList<>();
		if (!battery.isEmpty()) {
			warnings.add(BATTERY_WARNING);
		}
		if (m.windowEdgeUncertain()) {
			warnings.add(VIN_WARNING);
		}
		for (FaultHit h : m.faults()) {
			warnings.add("Risco " + tier(h.fault().tier()) + ": " + h.fault().fault()
					+ (h.fault().advice() == null ? "" : ". " + h.fault().advice()));
		}
		for (GearboxHit h : m.gearbox()) {
			warnings.add("Risco " + tier(h.rule().tier()) + ": " + gearbox(h) + " (\"" + h.matchedText() + "\")");
		}

		List<String> sources = Stream.of(rows.stream().map(SurveyResult::sourceUrl),
				battery.stream().map(SurveyResult::sourceUrl), m.faults().stream().map(h -> h.fault().sourceUrl()),
				m.gearbox().stream().map(h -> h.rule().sourceUrl())).flatMap(Function.identity()).distinct().toList();
		return new SubScore(value, confidence, text, sources, List.copyOf(warnings));
	}

	/**
	 * Series rows whose registration year (June, as in generation matching) lies inside every candidate's window, so
	 * a changeover car never uses a row that belongs to only one of its possible generations.
	 */
	private List<SurveyResult> seriesRows(CarMatch m) {
		if (m.candidates().isEmpty()) {
			return List.of();
		}
		Generation any = m.candidates().getFirst();
		return kb.seriesSurveys(any.make(), any.seriesModel()).stream()
				.filter(s -> s.regYear() != null
						&& m.candidates().stream().allMatch(g -> g.covers(YearMonth.of(s.regYear(), 6))))
				.toList();
	}

	/** Scorable and shows its measured age; battery-caused rows only warn. */
	private static boolean usable(SurveyResult s) {
		return s.cause() != Cause.BATTERY && s.ageYears() != null && s.referenceValue() != null
				&& (s.lowerIsBetter() ? s.referenceValue() : s.value()) > 0;
	}

	private static boolean covers(SurveyResult s, int age) {
		Matcher b = BAND.matcher(s.metric());
		int upper = b.find() ? Integer.parseInt(b.group(2)) : s.ageYears();
		return s.ageYears() <= age && age <= upper;
	}

	private static double rowScore(SurveyResult s) {
		return Ratio.toScore(s.lowerIsBetter() ? s.value() / s.referenceValue() : s.referenceValue() / s.value());
	}

	private static double tagFactor(String tag) {
		return switch (tag) {
			case "S" -> 0.8;
			case "I", "agg" -> 0.7;
			default -> 1.0;
		};
	}

	private static double penalty(Tier t) {
		return switch (t) {
			case HIGH -> 0.30;
			case MEDIUM -> 0.15;
			case LOW -> 0.0;
		};
	}

	private static String tier(Tier t) {
		return switch (t) {
			case HIGH -> "alto";
			case MEDIUM -> "médio";
			case LOW -> "baixo";
		};
	}

	private static String gearbox(GearboxHit h) {
		return switch (h.rule().type()) {
			case ROBOTISED -> "caixa robotizada";
			case DRY_DCT -> "caixa de dupla embraiagem a seco";
			case OTHER -> "caixa automática com avarias conhecidas";
		};
	}

	/** "TÜV 2019/2021, ADAC 2014": one entry per survey, keys sorted. */
	private static String label(List<SurveyResult> rows, Function<SurveyResult, String> key) {
		return rows.stream()
				.collect(groupingBy(s -> s.survey() == Survey.TUV ? "TÜV" : s.survey().name(), LinkedHashMap::new,
						mapping(key, toCollection(TreeSet::new))))
				.entrySet().stream().map(e -> e.getKey() + " " + String.join("/", e.getValue())).collect(joining(", "));
	}

	private static String ages(List<SurveyResult> rows) {
		return rows.stream().map(s -> String.valueOf(s.ageYears())).distinct().collect(joining("/"));
	}
}
