package carfinder.source;

/** A listing source failed; the message is PT-PT and shown to the user. */
public class SourceException extends RuntimeException {

	public SourceException(String message) {
		super(message);
	}

	public SourceException(String message, Throwable cause) {
		super(message, cause);
	}
}
