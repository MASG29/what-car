package carfinder.buying;

import carfinder.knowledge.Timing;
import carfinder.listing.Listing;
import carfinder.matching.CarMatch;
import carfinder.scoring.ConditionReader;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RealCostCalculator {

	/** Engine unknown or belt/unknown timing: the belt may be due. Chain engines never. */
	static boolean beltPossible(CarMatch m) {
		return m.engine().map(e -> e.timing() != Timing.CHAIN).orElse(true);
	}

	public RealCost estimate(Listing l, CarMatch m, RealCostSettings s) {
		Map<String, Double> items = new LinkedHashMap<>();
		if (l.getPriceEur() == null) {
			items.put("Preço pedido (desconhecido)", 0.0);
		} else {
			items.put("Preço pedido", (double) l.getPriceEur());
		}
		items.put("Papelada", (double) s.paperworkEur());
		items.put("Inspeção", s.ipoEur());
		Integer year = l.getYear();
		if (year == null) {
			items.put("IUC (fora do total: ano de matrícula desconhecido)", 0.0);
		} else if (year == 2007 && l.getMonth() == null) {
			// 2007 straddles the July cut-off: no neutral number, so take the higher IUC
			items.put("IUC (estimativa, mês desconhecido)", (double) s.iucPost2007Eur());
		} else {
			boolean pre = year < 2007 || (year == 2007 && l.getMonth() < 7);
			items.put("IUC (estimativa)", (double) (pre ? s.iucPre2007Eur() : s.iucPost2007Eur()));
		}
		boolean done = ConditionReader.read(l.getDescription()).maintenancePt().contains(ConditionReader.BELT_DONE);
		if (beltPossible(m) && !done) {
			items.put("Correia (assumida por fazer)", (double) s.beltEur());
		}
		double total = items.values().stream().mapToDouble(Double::doubleValue).sum();
		String insurance = "Seguro: cerca de " + s.insuranceMinEur() + " a " + s.insuranceMaxEur()
				+ " € por ano (não incluído no total)";
		return new RealCost(total, items, insurance);
	}
}
