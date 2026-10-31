package carfinder.knowledge;

import java.time.YearMonth;
import java.util.List;

/** prodEnd is null while the generation is still in production. */
public record Generation(String id, String make, String model, String code, String segment, YearMonth prodStart,
		YearMonth prodEnd, List<String> aliases, List<Integer> changeoverYears) {

	/** prod_start <= reg <= prod_end + 6 months; a car registered soon after the end is still this generation. */
	public boolean covers(YearMonth reg) {
		return !reg.isBefore(prodStart) && (prodEnd == null || !reg.isAfter(prodEnd.plusMonths(6)));
	}
}
