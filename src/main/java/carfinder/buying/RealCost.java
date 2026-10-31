package carfinder.buying;

import java.util.Map;

/** totalEur = asking price + items. Insurance is not in totalEur; it is only described in insuranceRangePt. */
public record RealCost(double totalEur, Map<String, Double> itemsPt, String insuranceRangePt) {
}
