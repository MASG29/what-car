package carfinder.knowledge;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class KnowledgeConfig {

	/** Loaded once; a bad CSV stops startup. */
	@Bean
	KnowledgeBase knowledgeBase() {
		return KnowledgeLoader.loadClasspath();
	}
}
