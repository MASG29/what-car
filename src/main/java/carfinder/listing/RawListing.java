package carfinder.listing;

/** Every field except site, externalId, url and title may be null; detail fields are null until the advert page is read. */
public record RawListing(
		Site site,
		String externalId,
		String url,
		String title,
		Integer priceEur,
		String make,
		String model,
		String version,
		Integer year,
		Integer month,
		Integer km,
		Fuel fuel,
		Integer cc,
		Integer powerCv,
		String gearbox,
		Integer doors,
		SellerType sellerType,
		String district,
		String city,
		int photoCount,
		String description,
		Boolean imported,
		boolean parts) {
}
