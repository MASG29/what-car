package carfinder.search;

import carfinder.buying.RealCostSettings;
import carfinder.listing.Fuel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Set;

/**
 * The user's limits. Price, km and year have no defaults. The cap defaults to the asking price (spec) and the cost
 * items to the report's values. models are "make model" strings; empty = every B/C model in the knowledge base.
 * minDoors is optional (null = any).
 */
public record SearchLimits(@Positive int maxPriceEur, PriceCap priceCap, @Positive int maxKm, @Positive int minYear,
		@NotEmpty Set<Fuel> fuels, Set<String> districts, List<String> models, @Valid RealCostSettings realCost,
		@Positive Integer minDoors) {

	public enum PriceCap { ASKING, REAL_COST }

	public SearchLimits {
		priceCap = priceCap == null ? PriceCap.ASKING : priceCap;
		fuels = fuels == null ? Set.of() : Set.copyOf(fuels);
		districts = districts == null ? Set.of() : Set.copyOf(districts);
		models = models == null ? List.of() : List.copyOf(models);
		realCost = realCost == null ? RealCostSettings.defaults() : realCost;
	}
}
