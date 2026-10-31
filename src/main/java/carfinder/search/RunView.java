package carfinder.search;

import java.util.UUID;

/** What the UI polls while a run is going. */
public record RunView(UUID id, RunStatus status, String messagePt, int collected) {
}
