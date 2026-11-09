package carfinder.web.dto;

/** read = vehicle ads in the file (one per id), added = new to the app, unavailable = seen sold or reserved. */
public record ImportResult(int read, int added, int unavailable) {
}
