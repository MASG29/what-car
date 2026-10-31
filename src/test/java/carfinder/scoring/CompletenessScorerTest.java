package carfinder.scoring;

import static carfinder.listing.TestListings.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import carfinder.listing.Fuel;
import carfinder.listing.Listing;
import carfinder.listing.RawListing;
import carfinder.listing.Site;
import org.junit.jupiter.api.Test;

class CompletenessScorerTest {

	private final CompletenessScorer scorer = new CompletenessScorer();
	private static final String LONG = "Carro muito bem cuidado, sempre em garagem, uso urbano, único dono, "
			+ "pneus novos e vidros eléctricos a funcionar bem. ";

	private static Listing ad(Integer km, Integer doors, String description, boolean detailFetched) {
		return of(new RawListing(Site.STANDVIRTUAL, "1", "https://example.test/1", "t", 1500, "Renault", "Clio", null,
				2009, 6, km, Fuel.PETROL, 1200, 75, "manual", doors, null, null, null, 0, description, null, false),
				detailFetched);
	}

	@Test
	void completeness_allFields_andMaintenanceMention() {
		SubScore plain = scorer.score(ad(100000, 5, LONG, true));
		assertThat(plain.value().getAsDouble()).isCloseTo(1.0, within(1e-9));
		assertThat(plain.confidence()).isEqualTo(1.0);
		SubScore half = scorer.score(ad(100000, 5, "Correia de distribuição trocada", true));
		assertThat(half.value().getAsDouble()).isCloseTo(0.8 + 0.1, within(1e-9)); // short text, mention bonus
		SubScore both = scorer.score(ad(100000, 5, LONG + "Correia de distribuição trocada em 2024.", true));
		assertThat(both.value().getAsDouble()).isCloseTo(1.0, within(1e-9)); // capped
	}

	@Test
	void completeness_missingKm_warns() {
		SubScore s = scorer.score(ad(null, 5, LONG, true));
		assertThat(s.warningsPt()).containsExactly("km não indicados");
		assertThat(s.value().getAsDouble()).isCloseTo(0.8 * 6 / 7 + 0.2, within(1e-9));
	}

	@Test
	void completeness_searchPageOnly_halfConfidence() {
		SubScore s = scorer.score(ad(100000, null, null, false));
		assertThat(s.confidence()).isEqualTo(0.5);
		assertThat(s.value().getAsDouble()).isCloseTo(0.8 * 6 / 7, within(1e-9));
	}
}
