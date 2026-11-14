package carfinder.source;

/**
 * The site refused the request (HTTP 403 or 429; 400 on Marketplace). A deliberate block: the run stops opening that
 * site's ad pages and never retries. Not a sign of a broken parser.
 */
public class BlockedException extends SourceException {

	private final int status;

	public BlockedException(String site, int status) {
		super(site + " recusou o pedido (HTTP " + status + ")");
		this.status = status;
	}

	public int status() {
		return status;
	}
}
