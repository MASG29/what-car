package carfinder.search;

import static carfinder.knowledge.Text.normalise;

import carfinder.listing.Listing;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** Groups ads that are the same car. The first ad of each group is the representative (most known fields). */
public final class Deduplicator {

	private static final int KM_GAP = 1000;
	private static final int PRICE_GAP_EUR = 50;

	private Deduplicator() {
	}

	public static List<List<Listing>> group(List<Listing> ads) {
		List<List<Listing>> groups = new ArrayList<>();
		for (Listing ad : ads) {
			groups.stream().filter(g -> g.stream().anyMatch(o -> same(ad, o))).findFirst()
					.ifPresentOrElse(g -> g.add(ad), () -> groups.add(new ArrayList<>(List.of(ad))));
		}
		// stable sort: ties keep input order
		groups.forEach(g -> g.sort(Comparator.comparingLong(Deduplicator::known).reversed()));
		return groups.stream().map(List::copyOf).toList();
	}

	// Limitation: make aliases ("VW" vs "Volkswagen") are not unified; add a KnowledgeBase lookup if Marketplace needs it.
	static boolean same(Listing a, Listing b) {
		return a.getMake() != null && b.getMake() != null && normalise(a.getMake()).equals(normalise(b.getMake()))
				&& a.getModel() != null && b.getModel() != null
				&& normalise(a.getModel()).equals(normalise(b.getModel()))
				&& a.getYear() != null && a.getYear().equals(b.getYear())
				&& (a.getKm() == null ? b.getKm() == null : b.getKm() != null && Math.abs(a.getKm() - b.getKm()) <= KM_GAP)
				&& a.getPriceEur() != null && b.getPriceEur() != null
				&& Math.abs(a.getPriceEur() - b.getPriceEur()) <= PRICE_GAP_EUR
				&& (a.getSite() != b.getSite() || normalise(a.getTitle()).equals(normalise(b.getTitle())));
	}

	private static long known(Listing l) {
		return Stream.of(l.getPriceEur(), l.getMake(), l.getModel(), l.getVersion(), l.getYear(), l.getMonth(),
				l.getKm(), l.getFuel(), l.getCc(), l.getPowerCv(), l.getGearbox(), l.getDoors(), l.getSellerType(),
				l.getDistrict(), l.getCity(), l.getDescription(), l.getImported()).filter(Objects::nonNull).count();
	}
}
