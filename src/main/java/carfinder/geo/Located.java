package carfinder.geo;

import java.util.Optional;

public record Located(Optional<Place> place, Precision precision) {
}
