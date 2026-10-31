package carfinder.matching;

import carfinder.knowledge.GearboxRule;

/** matchedText is the listing field (gearbox, version or title) the rule matched in. */
public record GearboxHit(GearboxRule rule, String matchedText) {
}
