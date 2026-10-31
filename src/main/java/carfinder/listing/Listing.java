package carfinder.listing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "listing", uniqueConstraints = @UniqueConstraint(columnNames = {"site", "external_id"}))
public class Listing {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	private Site site;
	@Enumerated(EnumType.STRING)
	private Fuel fuel;
	@Enumerated(EnumType.STRING)
	private SellerType sellerType;

	@Column(length = 4000)
	private String description;

	private String externalId;
	private String url;
	private String title;
	private Integer priceEur;
	private String make;
	private String model;
	private String version;
	private Integer year;
	private Integer month;
	private Integer km;
	private Integer cc;
	private Integer powerCv;
	private String gearbox;
	private Integer doors;
	private String district;
	private String city;
	private int photoCount;
	private Boolean imported;
	private boolean parts;

	private Instant firstSeen;
	private Instant lastSeen;
	private Instant detailFetchedAt;

	protected Listing() {
	}

	Listing(RawListing raw, Instant now) {
		site = raw.site();
		externalId = raw.externalId();
		firstSeen = now;
		merge(raw, now);
	}

	/** Required fields always overwrite; nullable fields only when non-null; photoCount only if > 0; parts only turns on. */
	void merge(RawListing raw, Instant now) {
		url = raw.url();
		title = raw.title();
		if (raw.priceEur() != null) {
			priceEur = raw.priceEur();
		}
		if (raw.make() != null) {
			make = raw.make();
		}
		if (raw.model() != null) {
			model = raw.model();
		}
		if (raw.version() != null) {
			version = raw.version();
		}
		if (raw.year() != null) {
			year = raw.year();
		}
		if (raw.month() != null) {
			month = raw.month();
		}
		if (raw.km() != null) {
			km = raw.km();
		}
		if (raw.fuel() != null) {
			fuel = raw.fuel();
		}
		if (raw.cc() != null) {
			cc = raw.cc();
		}
		if (raw.powerCv() != null) {
			powerCv = raw.powerCv();
		}
		if (raw.gearbox() != null) {
			gearbox = raw.gearbox();
		}
		if (raw.doors() != null) {
			doors = raw.doors();
		}
		if (raw.sellerType() != null) {
			sellerType = raw.sellerType();
		}
		if (raw.district() != null) {
			district = raw.district();
		}
		if (raw.city() != null) {
			city = raw.city();
		}
		if (raw.photoCount() > 0) {
			photoCount = raw.photoCount();
		}
		if (raw.description() != null) {
			description = raw.description();
		}
		if (raw.imported() != null) {
			imported = raw.imported();
		}
		// false means "unknown" on search pages, so it never clears a parts flag set from details.
		parts = parts || raw.parts();
		lastSeen = now;
	}

	void markDetailFetched(Instant now) {
		detailFetchedAt = now;
	}

	public Long getId() {
		return id;
	}

	public Site getSite() {
		return site;
	}

	public String getExternalId() {
		return externalId;
	}

	public String getUrl() {
		return url;
	}

	public String getTitle() {
		return title;
	}

	public Integer getPriceEur() {
		return priceEur;
	}

	public String getMake() {
		return make;
	}

	public String getModel() {
		return model;
	}

	public String getVersion() {
		return version;
	}

	public Integer getYear() {
		return year;
	}

	public Integer getMonth() {
		return month;
	}

	public Integer getKm() {
		return km;
	}

	public Fuel getFuel() {
		return fuel;
	}

	public Integer getCc() {
		return cc;
	}

	public Integer getPowerCv() {
		return powerCv;
	}

	public String getGearbox() {
		return gearbox;
	}

	public Integer getDoors() {
		return doors;
	}

	public SellerType getSellerType() {
		return sellerType;
	}

	public String getDistrict() {
		return district;
	}

	public String getCity() {
		return city;
	}

	public int getPhotoCount() {
		return photoCount;
	}

	public String getDescription() {
		return description;
	}

	public Boolean getImported() {
		return imported;
	}

	public boolean isParts() {
		return parts;
	}

	public Instant getFirstSeen() {
		return firstSeen;
	}

	public Instant getLastSeen() {
		return lastSeen;
	}

	public Instant getDetailFetchedAt() {
		return detailFetchedAt;
	}
}
