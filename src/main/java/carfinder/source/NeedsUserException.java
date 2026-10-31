package carfinder.source;

/**
 * A source needs the user (login, CAPTCHA or challenge page in the visible browser). The run waits and retries; the
 * tool never solves or bypasses the check itself. Not a {@link SourceException}: it must never count as a failed ad.
 */
public class NeedsUserException extends RuntimeException {

	public NeedsUserException(String messagePt) {
		super(messagePt);
	}
}
