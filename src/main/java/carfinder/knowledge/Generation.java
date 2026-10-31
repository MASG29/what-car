package carfinder.knowledge;

import java.time.YearMonth;
import java.util.List;

/** prodEnd is null while the generation is still in production. */
public record Generation(String id, String make, String model, String code, String segment, YearMonth prodStart,
		YearMonth prodEnd, List<String> aliases, List<Integer> changeoverYears) {
}
