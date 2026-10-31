package carfinder.scoring;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("carfinder.price")
public record PriceProperties(double inflatedRatio, double wellBelowRatio) {
}
