package carfinder.web.dto;

import carfinder.buying.Checklist;
import carfinder.buying.RealCost;
import carfinder.listing.Fuel;
import carfinder.listing.Listing;
import carfinder.listing.SellerType;
import carfinder.listing.Site;
import carfinder.scoring.Criterion;
import carfinder.scoring.Excluded;
import carfinder.scoring.ScoredListing;
import carfinder.scoring.ScoringService;
import carfinder.scoring.SubScore;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ranked ads, every excluded ad with its reasons, and how many ads each reason excluded (reason text before " (",
 * so "preço acima do teu limite (2600 €)" and "(2800 €)" count together). Nothing is dropped silently.
 */
public record RankingDto(List<Scored> ranked, List<Dropped> excluded, Map<String, Integer> excludedByReason) {

	/** value null = "sem dados". */
	public record Sub(Double value, double confidence, String explanationPt, List<String> sourceUrls,
			List<String> warningsPt) {
	}

	public record Ad(Site site, String url, String title, Integer priceEur, String make, String model, String version,
			Integer year, Integer month, Integer km, Fuel fuel, Integer cc, Integer powerCv, String gearbox,
			Integer doors, SellerType sellerType, String district, String city, int photoCount, String description,
			Boolean imported, boolean parts, boolean detailFetched) {
	}

	public record Scored(Ad listing, List<String> alsoOnUrls, Map<Criterion, Sub> subs, double total,
			double confidence, double rank, boolean top5, List<String> warningsPt, List<String> notesPt,
			RealCost realCost, Checklist checklist) {
	}

	public record Dropped(Ad listing, List<String> reasonsPt) {
	}

	public static RankingDto from(ScoringService.Ranking r) {
		Map<String, Integer> byReason = new LinkedHashMap<>();
		for (Excluded e : r.excluded()) {
			e.reasonsPt().stream().map(RankingDto::reasonKey).distinct().forEach(k -> byReason.merge(k, 1, Integer::sum));
		}
		return new RankingDto(r.ranked().stream().map(RankingDto::scored).toList(),
				r.excluded().stream().map(e -> new Dropped(ad(e.listing()), e.reasonsPt())).toList(), byReason);
	}

	static String reasonKey(String reason) {
		int cut = reason.indexOf(" (");
		return cut < 0 ? reason : reason.substring(0, cut);
	}

	private static Scored scored(ScoredListing s) {
		Map<Criterion, Sub> subs = new EnumMap<>(Criterion.class);
		s.subs().forEach((c, x) -> subs.put(c, sub(x)));
		return new Scored(ad(s.listing()), s.alsoOnUrls(), subs, s.total(), s.confidence(), s.rank(), s.top5(),
				s.warningsPt(), s.notesPt(), s.realCost(), s.checklist());
	}

	private static Sub sub(SubScore x) {
		return new Sub(x.value().isPresent() ? x.value().getAsDouble() : null, x.confidence(), x.explanationPt(),
				x.sourceUrls(), x.warningsPt());
	}

	private static Ad ad(Listing l) {
		return new Ad(l.getSite(), l.getUrl(), l.getTitle(), l.getPriceEur(), l.getMake(), l.getModel(),
				l.getVersion(), l.getYear(), l.getMonth(), l.getKm(), l.getFuel(), l.getCc(), l.getPowerCv(),
				l.getGearbox(), l.getDoors(), l.getSellerType(), l.getDistrict(), l.getCity(), l.getPhotoCount(),
				l.getDescription(), l.getImported(), l.isParts(), l.getDetailFetchedAt() != null);
	}
}
