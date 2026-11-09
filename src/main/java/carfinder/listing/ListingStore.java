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

	/** Only rows already stored change; an id never seen live is ignored. */
	@Transactional
	public void markUnavailable(Site site, String externalId) {
		repository.findBySiteAndExternalId(site, externalId).ifPresent(l -> {
			l.markUnavailable();
			repository.save(l);
		});
	}

	@Transactional
	public Listing applyDetails(Listing l, RawListing details) {
		Instant now = Instant.now();
		l.merge(details, now);
		l.markDetailFetched(now);
		return repository.save(l);
	}
}
