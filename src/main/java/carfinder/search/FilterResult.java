package carfinder.search;

import java.util.List;

/** notesPt holds every drop reason when keep is false, and keep-with-a-note remarks otherwise. */
public record FilterResult(boolean keep, List<String> notesPt) {
}
