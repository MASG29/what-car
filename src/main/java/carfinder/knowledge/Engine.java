package carfinder.knowledge;

import java.util.List;

public record Engine(String id, String make, String family, String fuel, Integer cc, List<Integer> kwList,
		Timing timing) {
}
