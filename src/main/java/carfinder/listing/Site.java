package carfinder.listing;

public enum Site {
	STANDVIRTUAL("Standvirtual"), MARKETPLACE("Marketplace"), CUSTOJUSTO("CustoJusto"), OLX("OLX");

	/** Name shown to the user. */
	public final String label;

	Site(String label) {
		this.label = label;
	}

	/** Ads come from HAR files the user imports (and, for Marketplace, its automated search), not from a fetched search. */
	public boolean importOnly() {
		return this == MARKETPLACE || this == OLX;
	}

	public String formatChangedPt() {
		return label + " mudou o formato da página";
	}
}
