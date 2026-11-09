package carfinder.source.marketplace;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("carfinder.marketplace")
/** maxDetails: advert pages per run; unset = every pre-filter survivor. */
public record MarketplaceProperties(String baseUrl, Integer maxDetails, long delayMinMs, long delayMaxMs,
		String userAgent) {
}
