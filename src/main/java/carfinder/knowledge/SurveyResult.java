package carfinder.knowledge;

/** generationId is null for model-series rows (ADAC by registration year). */
public record SurveyResult(String generationId, String make, String model, Survey survey, String edition,
		String metric, double value, Double referenceValue, boolean lowerIsBetter, Integer ageYears,
		Integer regYear, boolean combined, Cause cause, String sourceUrl, String tag) {
}
