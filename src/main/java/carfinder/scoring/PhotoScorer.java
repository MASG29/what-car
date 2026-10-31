package carfinder.scoring;

import carfinder.listing.Listing;
import java.util.List;
import java.util.OptionalDouble;
import org.springframework.stereotype.Component;

@Component
public class PhotoScorer {

	public SubScore score(Listing l) {
		// Search pages leave photoCount at 0 meaning "unknown"; only an advert page makes 0 a real zero.
		if (l.getPhotoCount() == 0 && l.getDetailFetchedAt() == null) {
			return new SubScore(OptionalDouble.empty(), 0, "sem dados de fotos", List.of(), List.of());
		}
		return new SubScore(OptionalDouble.of(Math.min(l.getPhotoCount() / 10.0, 1.0)), 1.0,
				l.getPhotoCount() + " fotos", List.of(), List.of());
	}
}
