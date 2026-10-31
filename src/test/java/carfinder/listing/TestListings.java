package carfinder.listing;

import java.time.Instant;

/** Builds a Listing for tests in other packages (the constructor is package-private). */
public final class TestListings {

	private TestListings() {
	}

	public static Listing listing(String make, String model, String version, Integer year, Integer month, Fuel fuel,
			Integer cc, Integer powerCv, String gearbox) {
		return new Listing(new RawListing(Site.STANDVIRTUAL, "test-1", "https://example.test/1", "Anúncio de teste",
				1500, make, model, version, year, month, 150000, fuel, cc, powerCv, gearbox, 5, null, null, null, 0,
				null, null, false), Instant.EPOCH);
	}
}
