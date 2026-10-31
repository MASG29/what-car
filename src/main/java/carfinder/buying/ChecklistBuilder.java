package carfinder.buying;

import carfinder.listing.Listing;
import carfinder.matching.CarMatch;
import carfinder.knowledge.Timing;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ChecklistBuilder {

	private static final List<Checklist.Link> LINKS = List.of(
			new Checklist.Link("Recolhas por fazer (chumbam na inspeção desde março de 2026)",
					"https://recall.motordata.pt"),
			new Checklist.Link("Histórico das inspeções (km em cada inspeção)",
					"https://www.gov.pt/servicos/obter-relatorio-de-resultados-das-inspecoes-tecnicas-de-veiculo"),
			new Checklist.Link("Certidão permanente (dívidas e reserva de propriedade)",
					"https://www.registoautomovel.pt/blog/certidao-permanente-automovel"));

	public Checklist build(Listing l, CarMatch m) {
		List<String> q = new ArrayList<>(List.of("Quantos km tem?", "Qual é a matrícula?"));
		if (RealCostCalculator.beltPossible(m)) {
			q.add("Quando foi trocada a correia de distribuição? Há fatura?");
		}
		if (m.engine().map(e -> e.timing() == Timing.CHAIN).orElse(false)) {
			q.add("Posso ouvir o motor a trabalhar a frio?");
		}
		if (l.getImported() == null) {
			q.add("O carro é nacional ou importado?");
		}
		return new Checklist(LINKS, q);
	}
}
