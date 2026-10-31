package carfinder.listing;

import static org.assertj.core.api.Assertions.assertThat;

import carfinder.TestcontainersConfiguration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({TestcontainersConfiguration.class, ListingStore.class})
class ListingStoreTest {

	@Autowired
	ListingStore store;

	@Autowired
	ListingRepository repository;

	private static RawListing raw(String externalId, Integer priceEur, Integer doors) {
		return new RawListing(Site.STANDVIRTUAL, externalId, "https://example.test/" + externalId,
				"Renault Clio", priceEur, "Renault", "Clio", null, 2015, 6, 120000, Fuel.DIESEL, 1461,
				90, null, doors, SellerType.UNKNOWN, null, null, 10, null, null, false);
	}

	@Test
	void upsert_insertsNewListing() {
		Listing l = store.upsert(raw("a1", 8000, null));

		assertThat(l.getId()).isNotNull();
		assertThat(l.getFirstSeen()).isNotNull();
		assertThat(repository.findBySiteAndExternalId(Site.STANDVIRTUAL, "a1")).isPresent();
	}

	@Test
	void upsert_sameSiteAndId_updatesPriceAndKeepsFirstSeen() {
		Listing first = store.upsert(raw("a2", 8000, null));
		Instant firstSeen = first.getFirstSeen();
		Instant firstLastSeen = first.getLastSeen();

		Listing second = store.upsert(raw("a2", 7500, null));

		assertThat(second.getId()).isEqualTo(first.getId());
		assertThat(second.getPriceEur()).isEqualTo(7500);
		assertThat(second.getFirstSeen()).isEqualTo(firstSeen);
		assertThat(second.getLastSeen()).isAfter(firstLastSeen);
		assertThat(repository.count()).isEqualTo(1);
	}

	@Test
	void upsert_nullDetailField_doesNotEraseStoredValue() {
		store.upsert(raw("a3", 8000, 5));

		Listing again = store.upsert(raw("a3", 8000, null));

		assertThat(again.getDoors()).isEqualTo(5);
	}

	@Test
	void applyDetails_setsDetailFetchedAt() {
		Listing l = store.upsert(raw("a4", 8000, null));
		assertThat(l.getDetailFetchedAt()).isNull();

		Listing detailed = store.applyDetails(l, raw("a4", null, 3));

		assertThat(detailed.getDetailFetchedAt()).isNotNull();
		assertThat(detailed.getDoors()).isEqualTo(3);
		assertThat(detailed.getPriceEur()).isEqualTo(8000);
	}

	@Test
	void upsert_afterDetailsWithParts_keepsParts() {
		Listing l = store.upsert(raw("a5", 8000, null));
		RawListing r = raw("a5", null, null);
		store.applyDetails(l, new RawListing(r.site(), r.externalId(), r.url(), r.title(), r.priceEur(), r.make(),
				r.model(), r.version(), r.year(), r.month(), r.km(), r.fuel(), r.cc(), r.powerCv(), r.gearbox(),
				r.doors(), r.sellerType(), r.district(), r.city(), r.photoCount(), r.description(), r.imported(),
				true));

		Listing again = store.upsert(raw("a5", 8000, null));

		assertThat(again.isParts()).isTrue();
	}
}
