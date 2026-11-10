package carfinder.source;

/** What a source needs to build its search URLs; km, year, make and model may be null (no limit). */
public record SearchQuery(int maxPriceEur, Integer maxKm, Integer minYear, String make, String model) {

	public SearchQuery(int maxPriceEur, Integer maxKm, Integer minYear) {
		this(maxPriceEur, maxKm, minYear, null, null);
	}
}
