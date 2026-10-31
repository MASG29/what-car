package carfinder.matching;

import carfinder.knowledge.EngineFault;

public record FaultHit(EngineFault fault, boolean uncertain) {
}
