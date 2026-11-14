package carfinder.search;

import static carfinder.knowledge.Text.normalise;

import carfinder.buying.RealCost;
import carfinder.geo.Located;
import carfinder.geo.Place;
import carfinder.geo.Places;
import carfinder.geo.Precision;
import carfinder.knowledge.Generation;
import carfinder.knowledge.KnowledgeBase;
import carfinder.listing.Listing;
import carfinder.listing.Site;
import carfinder.matching.CarMatch;
import carfinder.scoring.ConditionReader;
import carfinder.scoring.ConditionResult;
import carfinder.scoring.PriceResult;
import carfinder.search.SearchLimits.PriceCap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Applies the user's limits. Every drop carries a PT reason; unknown values never drop an ad on their own. */
@Component
public class ListingFilter {

	private static final int PLACEHOLDER_BELOW_EUR = 100;
	private static final Set<String> SEGMENTS = Set.of("b", "c");

	private final KnowledgeBase kb;
	private final Places places;

	public ListingFilter(KnowledgeBase kb, Places places) {
		this.kb = kb;
		this.places = places;
	}

	public FilterResult apply(Listing l, CarMatch m, PriceResult p, RealCost rc, SearchLimits s) {
		List<String> drop = new ArrayList<>();
		List<String> notes = new ArrayList<>();
		ConditionResult condition = ConditionReader.read(l.getTitle(), l.getDescription());
		if (l.isUnavailable()) {
			drop.add("vendido ou reservado");
		}
		if (l.isParts() || condition.parts()) {
			drop.add("anúncio de peças");
		}
		if (!condition.damagePt().isEmpty()) {
			if (Boolean.TRUE.equals(s.showDamaged())) {
				notes.add("custo da reparação: sem dados");
			} else {
				drop.add("precisa de reparação (" + String.join(", ", condition.damagePt()) + ")");
			}
		}
		// photoCount 0 is only real once the advert page was read; search pages leave it at 0. Marketplace never
		// drops for it: a logged-out page may not carry the photos, and Facebook requires one to list an item.
		if (l.getPhotoCount() == 0 && l.getDetailFetchedAt() != null && l.getSite() != Site.MARKETPLACE) {
			drop.add("sem fotos");
		}
		Integer price = l.getPriceEur();
		if (price == null) {
			drop.add("preço não indicado");
		} else if (price < PLACEHOLDER_BELOW_EUR) {
			drop.add("preço simbólico (" + price + " €), não é real");
		} else if (s.priceCap() == PriceCap.REAL_COST && rc.totalEur() > s.maxPriceEur()) {
			drop.add("custo real acima do teu limite (%.0f €)".formatted(rc.totalEur()));
		} else if (s.priceCap() == PriceCap.ASKING && price > s.maxPriceEur()) {
			drop.add("preço acima do teu limite (" + price + " €)");
		}
		if (p.inflated()) {
			drop.add("preço inflacionado face a anúncios comparáveis");
		}
		if (l.getYear() == null) {
			notes.add("ano não indicado");
		} else if (l.getYear() < s.minYear()) {
			drop.add("ano abaixo do teu limite (" + l.getYear() + ")");
		}
		if (l.getKm() != null && l.getKm() > s.maxKm()) {
			if (p.wellBelow()) {
				notes.add("km acima do teu limite (" + l.getKm() + " km), mas o preço compensa");
			} else {
				drop.add("km acima do teu limite (" + l.getKm() + " km)");
			}
		}
		if (l.getFuel() != null && !s.fuels().contains(l.getFuel())) {
			drop.add("combustível fora da tua escolha");
		}
		location(l, s, drop, notes);
		if (s.minDoors() != null) {
			if (l.getDoors() == null) {
				notes.add("portas não indicadas");
			} else if (l.getDoors() < s.minDoors()) {
				drop.add("menos portas do que pedes (" + l.getDoors() + ")");
			}
		}
		String model = modelProblem(l, s.models());
		if (model != null) {
			drop.add(model);
		}
		return drop.isEmpty() ? new FilterResult(true, List.copyOf(notes)) : new FilterResult(false, List.copyOf(drop));
	}

	/** Straight-line distance from the centre. Unknown or ambiguous places are kept, with a note saying why. */
	private void location(Listing l, SearchLimits s, List<String> drop, List<String> notes) {
		if (s.centre() == null || s.radiusKm() == null) {
			return;
		}
		Place centre = places.centre(s.centre());
		Located at = places.resolve(l.getCity(), l.getDistrict());
		switch (at.precision()) {
			case AMBIGUOUS -> notes.add("localidade ambígua (" + l.getCity() + ")");
			case UNKNOWN -> notes.add("localização desconhecida");
			default -> {
				long km = Math.round(Places.distanceKm(centre, at.place().orElseThrow()));
				if (km > s.radiusKm()) {
					drop.add("a ~" + km + " km de " + centre.name() + " (raio " + s.radiusKm() + " km)");
					return;
				}
				if (at.precision() == Precision.DISTRICT_ONLY) {
					notes.add("só sabemos o distrito (" + l.getDistrict() + ")");
				}
				notes.add("a ~" + km + " km em linha reta");
			}
		}
	}

	/** Null when the ad's model is wanted; otherwise the reason. Make aliases resolve through the knowledge base. */
	private String modelProblem(Listing l, List<String> models) {
		if (l.getMake() == null || l.getModel() == null) {
			// Marketplace, CustoJusto and OLX items name the car in the title (OLX also in fields the KB may not know): a make outside the knowledge base
			// is not recognised
			return l.getSite() == Site.MARKETPLACE || l.getSite() == Site.CUSTOJUSTO || l.getSite() == Site.OLX
					? "modelo ainda não está na base de conhecimento da app"
					: "marca ou modelo não indicados";
		}
		List<Generation> gens = kb.generationsFor(l.getMake(), l.getModel());
		if (models.isEmpty()) {
			return gens.stream().anyMatch(g -> SEGMENTS.contains(normalise(g.segment()))) ? null
					: "modelo fora dos segmentos B/C conhecidos";
		}
		Set<String> names = new HashSet<>();
		names.add(normalise(l.getMake() + " " + l.getModel()));
		for (Generation g : gens) {
			names.add(normalise(g.make() + " " + g.model()));
			g.aliases().forEach(a -> names.add(normalise(a + " " + g.model())));
		}
		return models.stream().map(x -> normalise(x)).anyMatch(names::contains) ? null
				: "modelo fora da tua escolha";
	}
}
