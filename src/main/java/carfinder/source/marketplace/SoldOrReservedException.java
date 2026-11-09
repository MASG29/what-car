package carfinder.source.marketplace;

import carfinder.source.SourceException;

/** The item page says the ad is sold or reserved; the run marks it unavailable instead of counting a failure. */
public class SoldOrReservedException extends SourceException {

	public SoldOrReservedException() {
		super("O anúncio do Marketplace foi vendido ou está reservado");
	}
}
