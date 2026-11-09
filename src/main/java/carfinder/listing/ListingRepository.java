package carfinder.listing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingRepository extends JpaRepository<Listing, Long> {

	Optional<Listing> findBySiteAndExternalId(Site site, String externalId);

	List<Listing> findByLastSeenAfter(Instant since);

	List<Listing> findBySite(Site site);
}
