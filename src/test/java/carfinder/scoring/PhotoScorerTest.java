package carfinder.scoring;

import static carfinder.listing.TestListings.of;
import static org.assertj.core.api.Assertions.assertThat;

import carfinder.listing.Fuel;
import carfinder.listing.Listing;
import carfinder.listing.RawListing;
import carfinder.listing.Site;
import org.junit.jupiter.api.Test;

class PhotoScorerTest {

	private final PhotoScorer scorer = new PhotoScorer();

	private static Listing ad(int photos, boolean detailFetched) {
		return of(new RawListing(Site.STANDVIRTUAL, "1", "https://example.test/1", "t", 1500, "Renault", "Clio", null,
				2009, 6, 1000, Fuel.PETROL, 1200, 75, "manual", 5, null, null, null, photos, null, null, false),
				detailFetched);
	}

	@Test
	void photos_capsAtTen() {
		assertThat(scorer.score(ad(4, false)).value().getAsDouble()).isEqualTo(0.4);
		assertThat(scorer.score(ad(25, true)).value().getAsDouble()).isEqualTo(1.0);
		assertThat(scorer.score(ad(25, true)).confidence()).isEqualTo(1.0);
	}

	@Test
	void photos_unknownOnSearchPage_semDados() {
		SubScore s = scorer.score(ad(0, false));
		assertThat(s.value()).isEmpty();
		assertThat(s.confidence()).isZero();
		assertThat(s.explanationPt()).isEqualTo("sem dados de fotos");
	}

	@Test
	void photos_zeroAfterAdvertRead_isRealZero() {
		SubScore s = scorer.score(ad(0, true));
		assertThat(s.value().getAsDouble()).isZero();
		assertThat(s.confidence()).isEqualTo(1.0);
	}
}
