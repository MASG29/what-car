package carfinder.scoring;

/** Weights per criterion. Custom weights are normalised to sum 1. */
public record Weights(double price, double reliability, double consumption, double completeness, double photos) {

	public double of(Criterion c) {
		return switch (c) {
			case PRICE -> price;
			case RELIABILITY -> reliability;
			case CONSUMPTION -> consumption;
			case COMPLETENESS -> completeness;
			case PHOTOS -> photos;
		};
	}

	public Weights normalised() {
		double sum = price + reliability + consumption + completeness + photos;
		if (price < 0 || reliability < 0 || consumption < 0 || completeness < 0 || photos < 0 || !(sum > 0)) {
			throw new IllegalArgumentException("Os pesos têm de ser positivos e não podem ser todos zero");
		}
		return new Weights(price / sum, reliability / sum, consumption / sum, completeness / sum, photos / sum);
	}
}
