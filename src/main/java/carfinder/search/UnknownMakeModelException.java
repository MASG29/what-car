package carfinder.search;

/** The typed make and model are not in the knowledge base; suggestion is the closest known one, or null. */
public class UnknownMakeModelException extends IllegalArgumentException {

	private final String suggestion;

	public UnknownMakeModelException(String typed, String suggestion) {
		super("Marca e modelo desconhecidos: " + typed + "."
				+ (suggestion == null ? " Escreve, por exemplo, Ford Fiesta." : " Será que querias dizer «" + suggestion + "»?"));
		this.suggestion = suggestion;
	}

	public String suggestion() {
		return suggestion;
	}
}
