package carfinder.listing;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListingStore {

	private final ListingRepository repository;

	public ListingStore(ListingRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public Listing upsert(RawListing raw) {
		Instant now = Instant.now();
		Listing l = repository.findBySiteAndExternalId(raw.site(), raw.externalId()).orElse(null);
		if (l == null) {
			return repository.save(new Listing(raw, now));
		}
		l.merge(raw, now);
		return repository.save(l);
	}

	@Transactional
	public Listing applyDetails(Listing l, RawListing details) {
		Instant now = Instant.now();
		l.merge(details, now);
		l.markDetailFetched(now);
		return repository.save(l);
	}
}
