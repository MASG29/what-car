package carfinder.scoring;

import carfinder.knowledge.Text;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads problem and maintenance phrases from an ad's description (normalised: no accents, lower case). A phrase
 * is ignored when "sem", "nao", "nunca" or "zero" comes before it in the same clause: a negation covers lists
 * joined by "," "e" "ou" "nem" and stops at . ! ? ; at "mas", "porem", "contudo", or at a new verb
 * ("tem", "tinha", "apresenta", "com"), so "Sem acidentes, tem riscos" counts "riscos". The title and the
 * description are read as two sentences, so a title never extends a description clause. "Não pega" and
 * "não anda" are damage phrases themselves and are never negated.
 */
public final class ConditionReader {

	private static final Map<String, Pattern> PROBLEMS = phrases(
			"riscos", "riscos?",
			"amolgadelas", "amolgad\\w*",
			"pintura danificada", "pintura (estalada|queimada|a descascar)",
			"ferrugem", "ferrugem",
			"embraiagem gasta", "embr[ae]i?agem (a patinar|gasta|a dar sinal)",
			"luz do airbag", "luz do airbag|airbag acesa",
			"luz do motor", "luz do motor",
			"fumo", "fumo",
			"barulho no motor", "barulho no motor",
			"caixa com problemas", "caixa (a raspar|com folga)");

	/** Maintenance label for a timing belt or kit said to be done. */
	public static final String BELT_DONE = "distribuição";

	private static final String DONE = "(nova|novo|mudada|mudado|substituida|substituido|trocada|trocado|feita|feito)";
	private static final String DONE_PLURAL = "(novos|mudados|substituidos|trocados)";
	// "embreagem" is the Brazilian spelling, common in ads
	private static final Map<String, Pattern> MAINTENANCE = phrases(
			"embraiagem", "embr[ae]i?agem " + DONE,
			"bomba de água", "bomba de agua " + DONE,
			"amortecedores", "amortecedores (novos|mudados|substituidos|trocados)",
			BELT_DONE, "(kit de distribuicao|correia( de distribuicao)?) " + DONE,
			"revisão", "revisao " + DONE,
			"pneus", "(?<!precisa de |precisa de \\d )pneus " + DONE_PLURAL,
			"seletores", "selec?tores " + DONE_PLURAL);

	/** Repair or accident phrases; an ad with one is dropped unless the user asked to see damaged cars. */
	private static final Map<String, Pattern> DAMAGE = phrases(
			"precisa de reparação", "precisa de (reparacao|arranjo)",
			"para reparar", "para reparar",
			"avariado", "avariad[oa]",
			"não pega", "nao pega",
			"não anda", "nao anda",
			"motor gripado", "motor (gripado|partido|rebentado)",
			"motor a bater", "motor a ?bater",
			"caixa partida", "caixa (partida|avariada)",
			"junta da cabeça", "junta (da|de) cabeca",
			"batido", "batido",
			"sinistrado", "sinistrado",
			"acidentado", "acidentado");
	private static final Set<String> NEVER_NEGATED = Set.of("não pega", "não anda");
	/**
	 * "para peças"; a sale of parts ("vendo peças", "vendo Clio às peças"); or a title or sentence that starts with
	 * "peças" ("Peças VW Polo"), unless it praises them ("Peças novas", "Peças originais", "Peças trocadas").
	 */
	private static final Pattern PARTS = Pattern.compile("\\b(so )?(vende se )?para pecas\\b"
			+ "|\\b(vendo|vende-?se|vende se) pecas\\b|\\b(vendo|vende-?se|vende se)( [^ .!?]+){0,3} as pecas\\b"
			+ "|(^|[.!?] )pecas\\b(?! (novas|originais|de origem|todas|trocadas|substituidas|mudadas))");

	private static final Set<String> NEGATIONS = Set.of("sem", "nao", "nunca", "zero");
	/** A new verb ("tem", "tinha", "apresenta", "com") ends the scope unless a negation sits right before it. */
	private static final Pattern CLAUSE_END = Pattern.compile(
			"[.!?;]|\\b(mas|porem|contudo)\\b|(?<!\\b(nao|nunca|nem) )\\b(tem|tinha|apresenta|com)\\b");

	private ConditionReader() {
	}

	public static ConditionResult read(String description) {
		return read(null, description);
	}

	/** Either text may be null. */
	public static ConditionResult read(String title, String description) {
		String joined = Stream.of(title, description).filter(t -> t != null && !t.isBlank())
				.collect(Collectors.joining(". "));
		if (joined.isEmpty()) {
			return new ConditionResult(List.of(), List.of(), List.of(), false);
		}
		// "nao fumador" is about the owner, not a negation of what follows ("Não fumador, riscos na porta")
		String text = Text.normalise(joined).replaceAll("\\bnao fumador", "fumador");
		Matcher parts = PARTS.matcher(text);
		boolean isParts = false;
		while (parts.find() && !isParts) {
			isParts = !negated(text.substring(0, parts.start()));
		}
		return new ConditionResult(found(PROBLEMS, text), found(MAINTENANCE, text), found(DAMAGE, text), isParts);
	}

	private static List<String> found(Map<String, Pattern> phrases, String text) {
		List<String> labels = new ArrayList<>();
		phrases.forEach((label, p) -> {
			Matcher m = p.matcher(text);
			while (m.find()) {
				if (NEVER_NEGATED.contains(label) || !negated(text.substring(0, m.start()))) {
					labels.add(label);
					break;
				}
			}
		});
		return labels;
	}

	private static boolean negated(String before) {
		String[] clauses = CLAUSE_END.split(before, -1);
		return Arrays.stream(clauses[clauses.length - 1].split("[^a-z0-9]+")).anyMatch(NEGATIONS::contains);
	}

	private static Map<String, Pattern> phrases(String... labelThenRegex) {
		Map<String, Pattern> m = new LinkedHashMap<>();
		for (int i = 0; i < labelThenRegex.length; i += 2) {
			m.put(labelThenRegex[i], Pattern.compile("\\b(" + labelThenRegex[i + 1] + ")\\b"));
		}
		return m;
	}
}
