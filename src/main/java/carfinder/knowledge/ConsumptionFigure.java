package carfinder.knowledge;

public record ConsumptionFigure(String generationId, String engineId, double lPer100km, Cycle cycle,
		String sourceUrl, String tag) {
}
