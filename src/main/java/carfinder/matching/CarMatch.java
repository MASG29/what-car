package carfinder.matching;

import carfinder.knowledge.Engine;
import carfinder.knowledge.Generation;
import java.util.List;
import java.util.Optional;

/** windowEdgeUncertain is true when any fault hit is uncertain: ask for the VIN or engine code. */
public record CarMatch(Optional<Generation> generation, List<Generation> candidates, boolean mixed,
		Optional<Engine> engine, List<FaultHit> faults, List<GearboxHit> gearbox, boolean windowEdgeUncertain) {
}
