package carfinder.source;

/** The ad page says the ad is sold, reserved or gone; the run marks it unavailable instead of counting a failure. */
public class SoldOrReservedException extends SourceException {

	public SoldOrReservedException() {
		this("O anúncio do Marketplace foi vendido ou está reservado");
	}

	public SoldOrReservedException(String message) {
		super(message);
	}
}
