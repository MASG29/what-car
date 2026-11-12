package carfinder.listing;

public enum Site {
	STANDVIRTUAL("Standvirtual"), MARKETPLACE("Marketplace"), CUSTOJUSTO("CustoJusto");

	/** Name shown to the user. */
	public final String label;

	Site(String label) {
		this.label = label;
	}

	public String formatChangedPt() {
		return label + " mudou o formato da página";
	}
}
