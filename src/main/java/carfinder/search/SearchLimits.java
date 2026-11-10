package carfinder.search;

import carfinder.buying.RealCostSettings;
import carfinder.listing.Fuel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Set;

/**
 * The user's limits. Price, km and year have no defaults. The cap defaults to the asking price (spec) and the cost
 * items to the report's values. models are "make model" strings; empty = every B/C model in the knowledge base.
 * minDoors is optional (null = any). centre and radiusKm are required to start a search (SearchService.start); null
 * means no location filter (runs saved before the centre existed, pasted links). Old JSON with "districts" still loads.
 * showDamaged (null = false) keeps ads that need repair, with a warning and a rank penalty; by default they are dropped.
 * makeModel is the user's typed "make model" (null = none); SearchService.start replaces models with it and sends it
 * to the site searches.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchLimits(@Positive int maxPriceEur, PriceCap priceCap, @Positive int maxKm, @Positive int minYear,
		@NotEmpty Set<Fuel> fuels, String centre, Integer radiusKm,
		List<String> models, @Valid RealCostSettings realCost,
		@Positive Integer minDoors, Boolean showDamaged, String makeModel) {

	public enum PriceCap { ASKING, REAL_COST }

	public SearchLimits {
		priceCap = priceCap == null ? PriceCap.ASKING : priceCap;
		fuels = fuels == null ? Set.of() : Set.copyOf(fuels);
		models = models == null ? List.of() : List.copyOf(models);
		realCost = realCost == null ? RealCostSettings.defaults() : realCost;
		makeModel = makeModel == null || makeModel.isBlank() ? null : makeModel.strip();
	}

	/** Limits without a typed make and model. */
	public SearchLimits(int maxPriceEur, PriceCap priceCap, int maxKm, int minYear, Set<Fuel> fuels, String centre,
			Integer radiusKm, List<String> models, RealCostSettings realCost, Integer minDoors, Boolean showDamaged) {
		this(maxPriceEur, priceCap, maxKm, minYear, fuels, centre, radiusKm, models, realCost, minDoors, showDamaged,
				null);
	}
}
