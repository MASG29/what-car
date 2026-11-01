package carfinder.scoring;

import carfinder.knowledge.Text;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads problem and maintenance phrases from an ad's description (normalised: no accents, lower case). A phrase
 * is ignored when "sem", "nao", "nunca" or "zero" is among the 3 words before it in the same sentence.
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

	private static final String DONE = "(nova|novo|mudada|mudado|substituida|substituido|trocada|trocado|feita|feito)";
	private static final Map<String, Pattern> MAINTENANCE = phrases(
			"embraiagem", "embraiagem " + DONE,
			"bomba de água", "bomba de agua " + DONE,
			"amortecedores", "amortecedores (novos|mudados|substituidos|trocados)",
			"distribuição", "kit de distribuicao|correia( de distribuicao)? " + DONE);

	private static final Set<String> NEGATIONS = Set.of("sem", "nao", "nunca", "zero");
	private static final Pattern SENTENCE_END = Pattern.compile("[.!?;]");
	private static final int WINDOW = 3;

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
		String[] sentences = SENTENCE_END.split(before, -1);
		String[] words = sentences[sentences.length - 1].split("[^a-z0-9]+");
		for (int i = Math.max(0, words.length - WINDOW); i < words.length; i++) {
			if (NEGATIONS.contains(words[i])) {
				return true;
			}
		}
		return false;
	}

	private static Map<String, Pattern> phrases(String... labelThenRegex) {
		Map<String, Pattern> m = new LinkedHashMap<>();
		for (int i = 0; i < labelThenRegex.length; i += 2) {
			m.put(labelThenRegex[i], Pattern.compile("\\b(" + labelThenRegex[i + 1] + ")\\b"));
		}
		return m;
	}
}
