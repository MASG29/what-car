package carfinder.search;

import carfinder.listing.Site;
import carfinder.scoring.Preset;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** One search: the limits it ran with, its status and the listings it collected. */
@Entity
@Table(name = "search_run")
public class SearchRun {

	@Id
	private UUID id;

	private String limitsJson;
	@Enumerated(EnumType.STRING)
	private Preset preset;
	private String customWeightsJson;

	// ponytail: EAGER, a run holds a few hundred ids at most; page them if runs grow to thousands.
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "search_run_site", joinColumns = @JoinColumn(name = "run_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "site")
	private Set<Site> sites = new HashSet<>();

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "search_run_listing", joinColumns = @JoinColumn(name = "run_id"))
	@Column(name = "listing_id")
	private Set<Long> listingIds = new HashSet<>();

	@Enumerated(EnumType.STRING)
	private RunStatus status;
	private String messagePt;
	private int collected;
	private Instant startedAt;
	private Instant finishedAt;

	protected SearchRun() {
	}

	SearchRun(UUID id, String limitsJson, Preset preset, String customWeightsJson, Set<Site> sites,
			Instant startedAt) {
		this.id = id;
		this.limitsJson = limitsJson;
		this.preset = preset;
		this.customWeightsJson = customWeightsJson;
		this.sites = new HashSet<>(sites);
		this.startedAt = startedAt;
		this.status = RunStatus.RUNNING;
		this.messagePt = "A iniciar a pesquisa";
	}

	void update(RunStatus status, String messagePt) {
		this.status = status;
		this.messagePt = messagePt;
	}

	void finish(RunStatus status, String messagePt, Instant at) {
		update(status, messagePt);
		finishedAt = at;
	}

	void addListings(Collection<Long> ids) {
		listingIds.addAll(ids);
		collected = listingIds.size();
	}

	public UUID getId() {
		return id;
	}

	public String getLimitsJson() {
		return limitsJson;
	}

	public Preset getPreset() {
		return preset;
	}

	public String getCustomWeightsJson() {
		return customWeightsJson;
	}

	public Set<Site> getSites() {
		return Set.copyOf(sites);
	}

	public Set<Long> getListingIds() {
		return Set.copyOf(listingIds);
	}

	public RunStatus getStatus() {
		return status;
	}

	public String getMessagePt() {
		return messagePt;
	}

	public int getCollected() {
		return collected;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}
}
