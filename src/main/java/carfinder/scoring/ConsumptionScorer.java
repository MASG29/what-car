package carfinder.scoring;

import carfinder.knowledge.ConsumptionFigure;
import carfinder.knowledge.KnowledgeBase;
import carfinder.matching.CarMatch;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import org.springframework.stereotype.Component;

/** Official figure for (generation, engine), ranked within segment and cycle. Cycles are never mixed. */
@Component
public class ConsumptionScorer {

	private static final Locale PT = Locale.of("pt", "PT");
	private final KnowledgeBase kb;

	ConsumptionScorer(KnowledgeBase kb) {
		this.kb = kb;
	}

	public SubScore score(CarMatch m) {
		var figure = m.generation().flatMap(g -> m.engine().flatMap(e -> kb.consumption(g.id(), e.id())));
		if (figure.isEmpty()) {
			return new SubScore(OptionalDouble.empty(), 0, "sem dados oficiais", List.of(), List.of());
		}
		ConsumptionFigure f = figure.get();
		var group = kb.consumptionInSegment(m.generation().get().segment(), f.cycle());
		double min = group.stream().mapToDouble(ConsumptionFigure::lPer100km).min().orElse(f.lPer100km());
		double max = group.stream().mapToDouble(ConsumptionFigure::lPer100km).max().orElse(f.lPer100km());
		boolean alone = group.size() < 2 || max == min;
		double value = alone ? 0.5 : (max - f.lPer100km()) / (max - min);
		return new SubScore(OptionalDouble.of(value), alone ? 0.5 : 1.0,
				"%s l/100 km (%s, oficial)".formatted(String.format(PT, "%.1f", f.lPer100km()), f.cycle()),
				List.of(f.sourceUrl()), List.of());
	}
}
