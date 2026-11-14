package carfinder.matching;

import carfinder.knowledge.Engine;
import carfinder.knowledge.Generation;
import java.util.List;
import java.util.Optional;

/**
 * windowEdgeUncertain is true when any fault hit is uncertain: ask for the VIN or engine code. engineOptions is how
 * many engines were still possible when none could be picked (1 when one was picked, 0 when none is known).
 */
public record CarMatch(Optional<Generation> generation, List<Generation> candidates, boolean mixed,
		Optional<Engine> engine, List<FaultHit> faults, List<GearboxHit> gearbox, boolean windowEdgeUncertain,
		int engineOptions) {

	/** Several engines fit the generation and nothing in the ad picks one. */
	public boolean severalEngines() {
		return !candidates.isEmpty() && engine.isEmpty() && engineOptions > 1;
	}
}
