package carfinder.scoring;

public enum Preset {
	BALANCED(new Weights(0.30, 0.30, 0.15, 0.15, 0.10)),
	ECONOMICAL(new Weights(0.40, 0.15, 0.30, 0.10, 0.05)),
	RELIABLE(new Weights(0.20, 0.50, 0.10, 0.15, 0.05)),
	CUSTOM(null);

	private final Weights weights;

	Preset(Weights weights) {
		this.weights = weights;
	}

	/** custom is only read for CUSTOM. */
	public Weights weights(Weights custom) {
		if (this != CUSTOM) {
			return weights;
		}
		if (custom == null) {
			throw new IllegalArgumentException("Pesos personalizados em falta");
		}
		return custom.normalised();
	}
}
