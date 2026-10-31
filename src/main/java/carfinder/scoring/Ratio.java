package carfinder.scoring;

public final class Ratio {

	private Ratio() {
	}

	/** r is value / reference (1 = average). */
	public static double toScore(double r) {
		return Math.clamp(1.5 - r, 0.0, 1.0);
	}
}
