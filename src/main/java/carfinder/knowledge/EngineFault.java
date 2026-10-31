package carfinder.knowledge;

import java.time.YearMonth;

public record EngineFault(String engineId, String fault, YearMonth buildFrom, YearMonth buildTo, Tier tier,
		String advice, String sourceUrl, String tag) {
}
