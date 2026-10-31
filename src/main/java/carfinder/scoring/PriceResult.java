package carfinder.scoring;

import java.util.OptionalDouble;

public record PriceResult(SubScore score, OptionalDouble ratio, boolean inflated, boolean wellBelow) {
}
