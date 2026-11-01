package carfinder.buying;

import jakarta.validation.constraints.PositiveOrZero;

/** All amounts are editable by the user. Defaults come from the research report (paperwork, inspection, belt). */
public record RealCostSettings(@PositiveOrZero int paperworkEur, @PositiveOrZero double ipoEur,
		@PositiveOrZero int iucPre2007Eur, @PositiveOrZero int iucPost2007Eur, @PositiveOrZero int beltEur,
		@PositiveOrZero int insuranceMinEur, @PositiveOrZero int insuranceMaxEur) {

	public static RealCostSettings defaults() {
		return new RealCostSettings(105, 37.47, 40, 149, 350, 800, 1500);
	}
}
