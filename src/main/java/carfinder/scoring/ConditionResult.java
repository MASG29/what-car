package carfinder.scoring;

import java.util.List;

/** PT labels ("riscos", "embraiagem") of the problems and recent maintenance the ad's text mentions. */
public record ConditionResult(List<String> problemsPt, List<String> maintenancePt, List<String> damagePt,
		boolean parts) {

	public ConditionResult {
		problemsPt = List.copyOf(problemsPt);
		maintenancePt = List.copyOf(maintenancePt);
		damagePt = List.copyOf(damagePt);
	}

	public ConditionResult(List<String> problemsPt, List<String> maintenancePt) {
		this(problemsPt, maintenancePt, List.of(), false);
	}

	/** Builder's call, not a sourced figure: +0.03 per maintenance item, -0.05 per problem, each capped at 3. */
	public double rankAdjustment() {
		return 0.03 * Math.min(3, maintenancePt.size()) - 0.05 * Math.min(3, problemsPt.size());
	}
}
