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

/**
 * Reads problem and maintenance phrases from an ad's description (normalised: no accents, lower case). A phrase
 * is ignored when "sem", "nao", "nunca" or "zero" comes before it in the same clause: a negation covers lists
 * joined by "," "e" "ou" "nem" and stops at . ! ? ; at "mas", "porem", "contudo", or at a new verb
 * ("tem", "tinha", "apresenta", "com"), so "Sem acidentes, tem riscos" counts "riscos".
 */
public final class ConditionReader {

	private static final Map<String, Pattern> PROBLEMS = phrases(
			"riscos", "riscos?",
			"amolgadelas", "amolgad\\w*",
			"pintura danificada", "pintura (estalada|queimada|a descascar)",
			"ferrugem", "ferrugem",
			"embraiagem gasta", "embraiagem (a patinar|gasta|a dar sinal)",
			"luz do airbag", "luz do airbag|airbag acesa",
			"luz do motor", "luz do motor",
			"fumo", "fumo",
			"barulho no motor", "barulho no motor",
			"caixa com problemas", "caixa (a raspar|com folga)");

	/** Maintenance label for a timing belt or kit said to be done. */
	public static final String BELT_DONE = "distribuição";

	private static final String DONE = "(nova|novo|mudada|mudado|substituida|substituido|trocada|trocado|feita|feito)";
	private static final Map<String, Pattern> MAINTENANCE = phrases(
			"embraiagem", "embraiagem " + DONE,
			"bomba de água", "bomba de agua " + DONE,
			"amortecedores", "amortecedores (novos|mudados|substituidos|trocados)",
			BELT_DONE, "(kit de distribuicao|correia( de distribuicao)?) " + DONE);

	private static final Set<String> NEGATIONS = Set.of("sem", "nao", "nunca", "zero");
	/** A new verb ("tem", "tinha", "apresenta", "com") ends the scope unless a negation sits right before it. */
	private static final Pattern CLAUSE_END = Pattern.compile(
			"[.!?;]|\\b(mas|porem|contudo)\\b|(?<!\\b(nao|nunca|nem) )\\b(tem|tinha|apresenta|com)\\b");

	private ConditionReader() {
	}

	public static ConditionResult read(String description) {
		if (description == null) {
			return new ConditionResult(List.of(), List.of());
		}
		// "nao fumador" is about the owner, not a negation of what follows ("Não fumador, riscos na porta")
		String text = Text.normalise(description).replaceAll("\\bnao fumador", "fumador");
		return new ConditionResult(found(PROBLEMS, text), found(MAINTENANCE, text));
	}

	private static List<String> found(Map<String, Pattern> phrases, String text) {
		List<String> labels = new ArrayList<>();
		phrases.forEach((label, p) -> {
			Matcher m = p.matcher(text);
			while (m.find()) {
				if (!negated(text.substring(0, m.start()))) {
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
