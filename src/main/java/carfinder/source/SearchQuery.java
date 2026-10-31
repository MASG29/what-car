package carfinder.source;

/** What a source needs to build its search URLs; km and year may be null (no limit). */
public record SearchQuery(int maxPriceEur, Integer maxKm, Integer minYear) {
}
