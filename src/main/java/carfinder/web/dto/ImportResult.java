package carfinder.web.dto;

/**
 * read = vehicle ads kept from the file (one per id), added = new to the app, unavailable = seen sold, reserved or
 * removed, skipped = another site's copies left out (OLX's Standvirtual ads).
 */
public record ImportResult(int read, int added, int unavailable, int skipped) {
}
