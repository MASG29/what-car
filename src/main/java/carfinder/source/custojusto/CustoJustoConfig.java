package carfinder.source.custojusto;

import carfinder.source.Pace;
import java.util.random.RandomGenerator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CustoJustoProperties.class)
class CustoJustoConfig {

	@Bean
	Pace custojustoPace(CustoJustoProperties p) {
		return new Pace(p.delayMinMs(), p.delayMaxMs(), RandomGenerator.getDefault());
	}
}
