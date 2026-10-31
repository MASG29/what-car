package carfinder.source;

import java.util.random.RandomGenerator;

/** Random human-like pause between requests. */
public class Pace {

	private final long minMs;
	private final long maxMs;
	private final RandomGenerator rng;

	public Pace(long minMs, long maxMs, RandomGenerator rng) {
		this.minMs = minMs;
		this.maxMs = maxMs;
		this.rng = rng;
	}

	public void between() {
		long ms = rng.nextLong(minMs, maxMs + 1);
		if (ms <= 0) {
			return;
		}
		try {
			Thread.sleep(ms);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new SourceException("Pesquisa interrompida", e);
		}
	}
}
