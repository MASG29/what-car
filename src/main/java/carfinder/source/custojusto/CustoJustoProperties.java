package carfinder.source.custojusto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** maxDetails: advert pages per run; unset = every pre-filter survivor. */
@ConfigurationProperties("carfinder.custojusto")
public record CustoJustoProperties(String baseUrl, int maxPages, Integer maxDetails, long delayMinMs, long delayMaxMs,
		String userAgent) {
}
