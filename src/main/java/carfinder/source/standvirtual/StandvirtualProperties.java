package carfinder.source.standvirtual;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("carfinder.standvirtual")
public record StandvirtualProperties(String baseUrl, int maxPages, long delayMinMs, long delayMaxMs,
		String userAgent) {
}
