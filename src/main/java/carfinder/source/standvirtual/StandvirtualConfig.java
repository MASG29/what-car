package carfinder.source.standvirtual;

import carfinder.source.Pace;
import java.util.random.RandomGenerator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StandvirtualProperties.class)
class StandvirtualConfig {

	@Bean
	Pace standvirtualPace(StandvirtualProperties p) {
		return new Pace(p.delayMinMs(), p.delayMaxMs(), RandomGenerator.getDefault());
	}
}
