package carfinder.buying;

/** All amounts are editable by the user. Defaults come from the research report (paperwork, inspection, belt). */
public record RealCostSettings(int paperworkEur, double ipoEur, int iucPre2007Eur, int iucPost2007Eur, int beltEur,
		int insuranceMinEur, int insuranceMaxEur) {

	public static RealCostSettings defaults() {
		return new RealCostSettings(105, 37.47, 40, 149, 350, 800, 1500);
	}
}
