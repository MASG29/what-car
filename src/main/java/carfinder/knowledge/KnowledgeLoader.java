package carfinder.knowledge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.MappingIterator;
import tools.jackson.dataformat.csv.CsvMapper;
import tools.jackson.dataformat.csv.CsvReadFeature;

/**
 * Reads and validates the knowledge CSVs. Every problem is an {@link IllegalStateException} worded
 * {@code <file>:<line>: <problem>}; the header is line 1. Quoted cells must not contain line breaks.
 */
public final class KnowledgeLoader {

	private static final CsvMapper CSV = CsvMapper.builder().enable(CsvReadFeature.WRAP_AS_ARRAY).build();
	private static final Set<String> TAGS = Set.of("P", "F", "S", "Q", "I", "agg");

	private KnowledgeLoader() {
	}

	/** Production path: works from a directory and from inside the packaged jar. */
	public static KnowledgeBase loadClasspath() {
		return load(name -> new ClassPathResource("knowledge/" + name).getInputStream());
	}

	public static KnowledgeBase load(Path dir) {
		return load(name -> Files.newInputStream(dir.resolve(name)));
	}

	@FunctionalInterface
	interface Opener {
		InputStream open(String name) throws IOException;
	}

	/** One CSV data row with checked accessors. */
	private static final class Row {
		private final String file;
		private final int line;
		private final Map<String, String> cells;

		Row(String file, int line, Map<String, String> cells) {
			this.file = file;
			this.line = line;
			this.cells = cells;
		}

		IllegalStateException error(String problem) {
			return new IllegalStateException(file + ":" + line + ": " + problem);
		}

		String opt(String col) {
			String v = cells.get(col).trim();
			return v.isEmpty() ? null : v;
		}

		String req(String col) {
			String v = opt(col);
			if (v == null) {
				throw error("missing " + col);
			}
			return v;
		}

		String source() {
			String url = req("source_url");
			if (!url.startsWith("http://") && !url.startsWith("https://")) {
				throw error("source_url is not an http(s) URL: " + url);
			}
			return url;
		}

		String tag() {
			String t = req("tag");
			if (!TAGS.contains(t)) {
				throw error("unknown tag " + t);
			}
			return t;
		}

		<E extends Enum<E>> E enumOf(String col, Class<E> type) {
			return enumOrNull(req(col), col, type);
		}

		<E extends Enum<E>> E enumOpt(String col, Class<E> type) {
			String v = opt(col);
			return v == null ? null : enumOrNull(v, col, type);
		}

		private <E extends Enum<E>> E enumOrNull(String v, String col, Class<E> type) {
			try {
				return Enum.valueOf(type, v);
			}
			catch (IllegalArgumentException e) {
				throw error("bad " + col + " " + v + ", expected one of " + Arrays.toString(type.getEnumConstants()));
			}
		}

		double number(String col) {
			return parse(req(col), col, Double::parseDouble);
		}

		Double numberOpt(String col) {
			String v = opt(col);
			return v == null ? null : parse(v, col, Double::parseDouble);
		}

		Integer intOpt(String col) {
			String v = opt(col);
			return v == null ? null : parse(v, col, Integer::parseInt);
		}

		boolean bool(String col) {
			String v = req(col);
			if (!v.equals("true") && !v.equals("false")) {
				throw error("bad " + col + " " + v + ", expected true or false");
			}
			return v.equals("true");
		}

		boolean boolOrFalse(String col) {
			return opt(col) != null && bool(col);
		}

		YearMonth month(String col) {
			String v = opt(col);
			return v == null ? null : parse(v, col, YearMonth::parse);
		}

		List<String> list(String col) {
			String v = opt(col);
			return v == null ? List.of() : Arrays.stream(v.split("\\|")).map(String::trim).toList();
		}

		List<Integer> intList(String col) {
			return list(col).stream().map(s -> parse(s, col, Integer::parseInt)).toList();
		}

		private <T> T parse(String v, String col, Function<String, T> fn) {
			try {
				return fn.apply(v);
			}
			catch (NumberFormatException | DateTimeParseException e) {
				throw error("bad " + col + " " + v);
			}
		}
	}

	static KnowledgeBase load(Opener opener) {
		List<Generation> generations = new ArrayList<>();
		Set<String> generationIds = new HashSet<>();
		for (Row r : rows(opener, "generations.csv", "generation_id", "make", "model", "code", "segment",
				"prod_start", "prod_end", "aliases", "changeover_years", "series_model")) {
			String id = r.req("generation_id");
			if (!generationIds.add(id)) {
				throw r.error("duplicate generation_id " + id);
			}
			generations.add(new Generation(id, r.req("make"), r.req("model"), r.opt("code"), r.req("segment"),
					r.month("prod_start"), r.month("prod_end"), r.list("aliases"), r.intList("changeover_years"),
					r.opt("series_model") == null ? r.req("model") : r.opt("series_model")));
		}

		List<Engine> engines = new ArrayList<>();
		Set<String> engineIds = new HashSet<>();
		for (Row r : rows(opener, "engines.csv", "engine_id", "make", "family", "fuel", "cc", "kw_list", "timing")) {
			String id = r.req("engine_id");
			if (!engineIds.add(id)) {
				throw r.error("duplicate engine_id " + id);
			}
			engines.add(new Engine(id, r.req("make"), r.req("family"), r.opt("fuel"), r.intOpt("cc"),
					r.intList("kw_list"), r.enumOf("timing", Timing.class)));
		}

		List<SurveyResult> surveys = new ArrayList<>();
		for (Row r : rows(opener, "survey_results.csv", "generation_id", "make", "model", "survey", "edition",
				"metric", "value", "reference_value", "lower_is_better", "age_years", "reg_year", "combined",
				"cause", "source_url", "tag")) {
			String gen = known(r, "generation_id", generationIds, false);
			surveys.add(new SurveyResult(gen, r.req("make"), r.req("model"), r.enumOf("survey", Survey.class),
					r.req("edition"), r.req("metric"), r.number("value"), r.numberOpt("reference_value"),
					r.bool("lower_is_better"), r.intOpt("age_years"), r.intOpt("reg_year"),
					r.boolOrFalse("combined"), r.enumOpt("cause", Cause.class), r.source(), r.tag()));
		}

		Map<String, List<String>> fitment = new LinkedHashMap<>();
		for (Row r : rows(opener, "engine_fitment.csv", "generation_id", "engine_id")) {
			fitment.computeIfAbsent(known(r, "generation_id", generationIds, true), k -> new ArrayList<>())
					.add(known(r, "engine_id", engineIds, true));
		}

		List<EngineFault> faults = new ArrayList<>();
		for (Row r : rows(opener, "engine_faults.csv", "engine_id", "fault", "build_from", "build_to", "tier",
				"advice", "source_url", "tag")) {
			faults.add(new EngineFault(known(r, "engine_id", engineIds, true), r.req("fault"),
					r.month("build_from"), r.month("build_to"), r.enumOf("tier", Tier.class), r.opt("advice"),
					r.source(), r.tag()));
		}

		List<GearboxRule> gearbox = new ArrayList<>();
		for (Row r : rows(opener, "gearbox_rules.csv", "keyword", "type", "tier", "source_url", "tag")) {
			gearbox.add(new GearboxRule(r.req("keyword"), r.enumOf("type", GearboxType.class),
					r.enumOf("tier", Tier.class), r.source(), r.tag()));
		}

		List<ConsumptionFigure> consumption = new ArrayList<>();
		for (Row r : rows(opener, "consumption.csv", "generation_id", "engine_id", "l_per_100km", "cycle",
				"source_url", "tag")) {
			consumption.add(new ConsumptionFigure(known(r, "generation_id", generationIds, true),
					known(r, "engine_id", engineIds, true), r.number("l_per_100km"),
					r.enumOf("cycle", Cycle.class), r.source(), r.tag()));
		}

		return new KnowledgeBase(generations, surveys, engines, fitment, faults, gearbox, consumption);
	}

	private static String known(Row r, String col, Set<String> ids, boolean required) {
		String v = required ? r.req(col) : r.opt(col);
		if (v != null && !ids.contains(v)) {
			throw r.error("unknown " + col + " " + v);
		}
		return v;
	}

	private static List<Row> rows(Opener opener, String file, String... header) {
		List<Row> rows = new ArrayList<>();
		try (InputStream in = opener.open(file); MappingIterator<String[]> it = CSV.readerFor(String[].class).readValues(in)) {
			if (!it.hasNext() || !Arrays.equals(Arrays.stream(it.next()).map(String::trim).toArray(), header)) {
				throw new IllegalStateException(file + ":1: header must be " + String.join(",", header));
			}
			int line = 1;
			while (it.hasNext()) {
				String[] cells = it.next();
				line++;
				if (cells.length != header.length) {
					throw new IllegalStateException(file + ":" + line + ": expected " + header.length
							+ " columns, found " + cells.length);
				}
				Map<String, String> map = new LinkedHashMap<>();
				for (int i = 0; i < header.length; i++) {
					map.put(header[i], cells[i] == null ? "" : cells[i]);
				}
				rows.add(new Row(file, line, map));
			}
		}
		catch (IOException e) {
			throw new IllegalStateException(file + ":1: cannot read file: " + e.getMessage(), e);
		}
		return rows;
	}
}
