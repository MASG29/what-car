package carfinder.source.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import carfinder.knowledge.KnowledgeLoader;
import carfinder.listing.Fuel;
import carfinder.source.marketplace.MarketplaceTitle.TitleFacts;
import org.junit.jupiter.api.Test;

class MarketplaceTitleTest {

	private final MarketplaceTitle title = new MarketplaceTitle(KnowledgeLoader.loadClasspath());

	@Test
	void yearMakeModelFuel() {
		assertThat(title.read("2006 VW Polo 1.4 tdi"))
				.isEqualTo(new TitleFacts("Volkswagen", "Polo", 2006, Fuel.DIESEL));
		assertThat(title.read("2012 Ford fiesta 1.4tdci"))
				.isEqualTo(new TitleFacts("Ford", "Fiesta", 2012, Fuel.DIESEL));
		assertThat(title.read("2009 Fiat Grande Punto 1.2 gasolina"))
				.isEqualTo(new TitleFacts("Fiat", "Grande Punto", 2009, Fuel.PETROL));
		assertThat(title.read("Peugeot 206 1.4 HDi")).isEqualTo(new TitleFacts("Peugeot", "206", null, Fuel.DIESEL));
		assertThat(title.read("Renault Clio GPL")).isEqualTo(new TitleFacts("Renault", "Clio", null, Fuel.LPG));
	}

	@Test
	void makeNotInKnowledgeBase_yearAndFuelStillRead() {
		// Opel is not in the knowledge base: the ad is later excluded "marca ou modelo não indicados", with its link
		assertThat(title.read("2010 Opel astra gtc 1.3 cdti")).isEqualTo(new TitleFacts(null, null, 2010, Fuel.DIESEL));
		assertThat(title.read("1998 Hyundai Coupé")).isEqualTo(new TitleFacts(null, null, 1998, null));
	}

	@Test
	void noYearNoFuel() {
		assertThat(title.read("Toyota yaris")).isEqualTo(new TitleFacts("Toyota", "Yaris", null, null));
		assertThat(title.read("Golf 2005").year()).isEqualTo(2005);
		assertThat(title.read("")).isEqualTo(new TitleFacts(null, null, null, null));
	}

	@Test
	void kmShapes() {
		assertThat(MarketplaceTitle.km("146K km")).isEqualTo(146000);
		assertThat(MarketplaceTitle.km("1.5K km")).isEqualTo(1500);
		assertThat(MarketplaceTitle.km("99K miles")).isEqualTo(159325);
		assertThat(MarketplaceTitle.km("")).isNull();
		assertThat(MarketplaceTitle.km(null)).isNull();
		assertThat(MarketplaceTitle.km("12 km")).isEqualTo(12);
		assertThat(MarketplaceTitle.km("Ponte de Lima")).isNull();
	}

	@Test
	void yearAnywhereInTitle_whenItIsTheOnlyOne() {
		assertThat(title.read("Oportunidade!!! Vendo Ford Fiesta 1998, 1.25 gasolina"))
				.isEqualTo(new TitleFacts("Ford", "Fiesta", 1998, Fuel.PETROL));
		assertThat(title.read("Renault Clio de 2003").year()).isEqualTo(2003);
		assertThat(title.read("Peugeot 2008 1.2").year()).isNull(); // a model name
		assertThat(title.read("Clio 2003 ou troco por carro de 2010").year()).isNull(); // two years
		assertThat(title.read("Golf 2000 km").year()).isNull();
		assertThat(title.read("Polo 1400 cc").year()).isNull();
		assertThat(title.read("Clio 2099").year()).isNull(); // the future
	}
}
