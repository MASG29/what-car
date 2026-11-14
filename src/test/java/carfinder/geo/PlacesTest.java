package carfinder.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class PlacesTest {

	private final Places places = new Places(Paths.get("src/test/resources/fixtures/geo/places-test.tsv"));

	@Test
	void centre_ignoresCaseAndAccents() {
		for (String typed : new String[] {"ponte de lima", "Ponte De Lima", "  PONTE  DE LIMA "}) {
			Place p = places.centre(typed);
			assertThat(p.lat()).isEqualTo(41.76719);
			assertThat(p.lon()).isEqualTo(-8.58393);
			assertThat(p.district()).isEqualTo("Viana do Castelo");
		}
	}

	@Test
	void distance_ponteDeLimaToBraga() {
		Place pl = places.centre("Ponte de Lima");
		assertThat(Places.distanceKm(pl, places.centre("Braga"))).isBetween(27.0, 28.0);
		assertThat(Places.distanceKm(pl, places.centre("Porto, Porto"))).isBetween(68.3, 69.3);
	}

	@Test
	void resolve_uniqueCity_exact() {
		Located l = places.resolve("Paramos", null);
		assertThat(l.precision()).isEqualTo(Precision.EXACT);
		assertThat(l.place().orElseThrow().district()).isEqualTo("Aveiro");
	}

	@Test
	void resolve_duplicatedName_noDistrict_ambiguous() {
		Located l = places.resolve("Porto", null);
		assertThat(l.precision()).isEqualTo(Precision.AMBIGUOUS);
		assertThat(l.place()).isEmpty();
	}

	@Test
	void resolve_duplicatedName_withDistrict_exact() {
		Located l = places.resolve("Porto", "porto");
		assertThat(l.precision()).isEqualTo(Precision.EXACT);
		assertThat(l.place().orElseThrow().lat()).isEqualTo(41.1485);
	}

	@Test
	void resolve_nameNotInTheAdsDistrict_districtCapital() {
		// never the namesake in another district: the distance would come from the wrong place
		for (String city : new String[] {"Porto", "Lisboa"}) {
			Located l = places.resolve(city, "Braga");
			assertThat(l.precision()).as(city).isEqualTo(Precision.DISTRICT_ONLY);
			assertThat(l.place().orElseThrow().name()).isEqualTo("Braga");
		}
	}

	@Test
	void resolve_unknownDistrictName_ignored() {
		assertThat(places.resolve("Lisboa", "Distrito Falso").precision()).isEqualTo(Precision.EXACT);
	}

	@Test
	void resolve_townAndMunicipality_sameDistrict_onePlace() {
		Located l = places.resolve("Ponte de Lima", null);
		assertThat(l.precision()).isEqualTo(Precision.EXACT);
		assertThat(l.place().orElseThrow().lat()).isEqualTo(41.76719); // the PPLA2 row, not the ADM2 one
	}

	@Test
	void resolve_alternateName() {
		Located l = places.resolve("Lisboa", null);
		assertThat(l.precision()).isEqualTo(Precision.EXACT);
		assertThat(l.place().orElseThrow().district()).isEqualTo("Lisboa");
	}

	@Test
	void resolve_districtOnly() {
		for (String city : new String[] {null, "Sítio Que Não Existe"}) {
			Located l = places.resolve(city, "Braga");
			assertThat(l.precision()).isEqualTo(Precision.DISTRICT_ONLY);
			assertThat(l.place().orElseThrow().name()).isEqualTo("Braga");
			assertThat(l.place().orElseThrow().lat()).isEqualTo(41.5514);
		}
		assertThat(places.resolve(null, "Madeira").place().orElseThrow().name()).isEqualTo("Funchal");
	}

	@Test
	void resolve_nothing_unknown() {
		assertThat(places.resolve(null, null).precision()).isEqualTo(Precision.UNKNOWN);
		assertThat(places.resolve("Sítio Que Não Existe", "Distrito Falso").precision()).isEqualTo(Precision.UNKNOWN);
	}

	@Test
	void centre_unknown_throwsPt() {
		assertThatThrownBy(() -> places.centre("Atlantida")).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Localidade desconhecida: Atlantida");
		assertThatThrownBy(() -> places.centre(" ")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void centre_ambiguous_throwsPtAndAcceptsNameCommaDistrict() {
		assertThatThrownBy(() -> places.centre("Porto")).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Localidade ambígua: Porto. Escreve \"Porto, Distrito\".");
		assertThat(places.centre("Porto, Porto").lat()).isEqualTo(41.1485);
		assertThat(places.centre("porto , viana do castelo").lat()).isEqualTo(42.0213);
	}

	@Test
	void realFile_loadsAndResolvesTheCentre() {
		Places real = Places.loadClasspath();
		assertThat(real.centre("Ponte de Lima").lat()).isEqualTo(41.76719);
		assertThat(real.centre("Lisboa").district()).isEqualTo("Lisboa");
	}
}
