package carfinder.source;


/** The item page needs a login; the ad keeps its imported data. Not a sign of a broken parser. */
public class LoginWallException extends SourceException {

	public LoginWallException() {
		super("O Facebook não mostra este anúncio sem login; fica com os dados importados");
	}
}
