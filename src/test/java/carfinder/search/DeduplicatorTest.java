package carfinder.search;

import static org.assertj.core.api.Assertions.assertThat;

import carfinder.listing.Fuel;
import carfinder.listing.Listing;
import carfinder.listing.RawListing;
import carfinder.listing.Site;
import carfinder.listing.TestListings;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeduplicatorTest {

	static Listing ad(Site site, String id, String title, Integer price, Integer km, Integer cc) {
		return TestListings.of(new RawListing(site, id, "https://example.test/" + id, title, price, "Toyota",
				"Yaris", null, 2008, null, km, Fuel.PETROL, cc, null, null, null, null, null, null, 0, null, null,
				false), false);
	}

	@Test
	void dedupe_crossSiteSameCar_grouped() {
		Listing sv = ad(Site.STANDVIRTUAL, "a", "Toyota Yaris 1.0", 2000, 180000, 998);
		Listing mp = ad(Site.MARKETPLACE, "b", "yaris impecável", 2030, 180600, null);
		List<List<Listing>> groups = Deduplicator.group(List.of(mp, sv));
		assertThat(groups).hasSize(1);
		assertThat(groups.getFirst()).containsExactly(sv, mp); // the ad with more fields first
	}

	@Test
	void dedupe_differentKm_notGrouped() {
		Listing a = ad(Site.STANDVIRTUAL, "a", "Toyota Yaris", 2000, 180000, 998);
		Listing b = ad(Site.MARKETPLACE, "b", "Toyota Yaris", 2000, 181500, 998);
		assertThat(Deduplicator.group(List.of(a, b))).hasSize(2);
	}

	@Test
	void sameSiteDifferentTitle_notGrouped_sameTitle_grouped() {
		Listing a = ad(Site.STANDVIRTUAL, "a", "Toyota Yaris", 2000, 180000, 998);
		Listing b = ad(Site.STANDVIRTUAL, "b", "Yaris 1.0 VVT-i", 2000, 180000, 998);
		Listing c = ad(Site.STANDVIRTUAL, "c", "toyota  yaris", 2000, 180000, 998);
		assertThat(Deduplicator.group(List.of(a, b, c))).containsExactly(List.of(a, c), List.of(b));
	}

	@Test
	void priceGapOver50_orUnknownFields_notGrouped() {
		Listing a = ad(Site.STANDVIRTUAL, "a", "t", 2000, 180000, 998);
		Listing b = ad(Site.MARKETPLACE, "b", "t", 2051, 180000, 998);
		Listing noPrice = ad(Site.MARKETPLACE, "c", "t", null, 180000, 998);
		assertThat(Deduplicator.group(List.of(a, b, noPrice))).hasSize(3);
	}

	@Test
	void bothKmNull_grouped() {
		Listing a = ad(Site.STANDVIRTUAL, "a", "t", 2000, null, 998);
		Listing b = ad(Site.MARKETPLACE, "b", "t", 2000, null, 998);
		assertThat(Deduplicator.group(List.of(a, b))).hasSize(1);
	}
}
