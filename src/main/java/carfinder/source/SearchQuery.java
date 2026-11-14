package carfinder.source;

/**
 * What a source needs to build its search URLs; km, year, make and model may be null (no limit). {@code automated}:
 * Marketplace may also search on its own (secondary account, Marco's choice per run).
 */
public record SearchQuery(int maxPriceEur, Integer maxKm, Integer minYear, String make, String model,
		boolean automated) {
}
