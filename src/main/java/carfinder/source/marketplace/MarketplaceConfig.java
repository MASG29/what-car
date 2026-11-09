package carfinder.source.marketplace;

import carfinder.source.Pace;
import java.util.random.RandomGenerator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MarketplaceProperties.class)
class MarketplaceConfig {

	@Bean
	Pace marketplacePace(MarketplaceProperties p) {
		return new Pace(p.delayMinMs(), p.delayMaxMs(), RandomGenerator.getDefault());
	}
}
