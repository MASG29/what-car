package carfinder.source.marketplace;

import carfinder.source.NeedsUserException;
import carfinder.source.Pace;
import carfinder.source.SourceException;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The automated Marketplace search, for a secondary account only: a visible browser window on its own profile folder,
 * where the user logs in by hand. It opens the search link, scrolls within a budget at the Marketplace pace, and keeps
 * the feed bodies the page itself receives. It never requests the feed API, never clicks anything, and stops at the
 * first verification screen. Plain Playwright: no stealth, no changed fingerprint, no imitated input.
 */
@Component
public class MarketplaceBrowser {

	static final String LOGIN_PT = "Faz login na janela aberta";
	static final String VERIFICATION_PT = "O Facebook pediu uma verificação; a pesquisa automática parou";
	private static final String CLOSED_PT = "A janela do navegador fechou ou falhou; a pesquisa automática parou";
	private static final String VERIFICATION_DOM = "form[action*='/checkpoint'], iframe[src*='captcha']";
	private static final double FEED_WAIT_MS = 10_000;

	private final Path profile;
	private final boolean headless;
	private final int maxScrolls;
	private final Pace pace;
	/** Optional: a full-page screenshot of the last search, so the collected ads can be checked by eye. */
	private Path screenshotDir;
	private Playwright playwright;
	private BrowserContext context;
	private Page page;

	public MarketplaceBrowser(@Value("${carfinder.marketplace.profile-dir}") String profile,
			@Value("${carfinder.marketplace.headless}") boolean headless,
			@Value("${carfinder.marketplace.max-scrolls}") int maxScrolls, @Qualifier("marketplacePace") Pace pace) {
		this.profile = Path.of(profile);
		this.headless = headless;
		this.maxScrolls = maxScrolls;
		this.pace = pace;
	}

	@Value("${carfinder.marketplace.screenshot-dir:}")
	void setScreenshotDir(String dir) {
		this.screenshotDir = dir.isBlank() ? null : Path.of(dir);
	}

	/**
	 * The page document and the feed bodies seen while scrolling. Without a session it opens the login page once and
	 * throws {@link NeedsUserException}, leaving the window open; retries while the user logs in reload nothing.
	 */
	public synchronized List<String> collect(String searchUrl, Consumer<String> progress) {
		URI search = URI.create(searchUrl);
		String origin = search.getScheme() + "://" + search.getRawAuthority();
		Predicate<Response> isFeed = r -> r.url().startsWith(origin + "/api/graphql/");
		List<Response> feed = new ArrayList<>();
		Consumer<Response> keep = r -> {
			if (isFeed.test(r)) {
				feed.add(r);
			}
		};
		try {
			open();
			guard();
			if (context.cookies(origin).stream().noneMatch(c -> "c_user".equals(c.name))) {
				if (!onLogin()) {
					page.navigate(origin + "/login/");
					guard();
				}
				throw new NeedsUserException(LOGIN_PT);
			}
			progress.accept("Pesquisa automática: a abrir o Marketplace");
			page.onResponse(keep);
			Response document = page.navigate(searchUrl);
			guard();
			if (onLogin()) {
				throw new NeedsUserException(LOGIN_PT);
			}
			List<String> bodies = new ArrayList<>();
			if (document != null) {
				bodies.add(document.text());
			}
			for (int i = 1; i <= maxScrolls; i++) {
				pace.between(); // a rate limit, not a disguise
				progress.accept("Pesquisa automática: a descer na página (" + i + " de " + maxScrolls + ")");
				try {
					page.waitForResponse(isFeed, new Page.WaitForResponseOptions().setTimeout(FEED_WAIT_MS),
							() -> page.evaluate("window.scrollTo(0, document.body.scrollHeight)"));
				}
				catch (TimeoutError e) {
					// nothing new loaded for this scroll; the next one may still load more
				}
				guard();
			}
			if (screenshotDir != null) {
				page.screenshot(new Page.ScreenshotOptions().setFullPage(true)
						.setPath(screenshotDir.resolve("marketplace-" + System.currentTimeMillis() + ".png")));
			}
			page.offResponse(keep); // reading a body lets more responses in; the batch ends here
			for (Response r : List.copyOf(feed)) {
				try {
					bodies.add(r.text());
				}
				catch (PlaywrightException e) {
					// body no longer available; its ads come with the next run
				}
			}
			close();
			return bodies;
		}
		catch (NeedsUserException e) {
			page.offResponse(keep);
			throw e; // the window stays open for the login
		}
		catch (PlaywrightException e) {
			close();
			throw new SourceException(CLOSED_PT, e);
		}
		catch (RuntimeException e) {
			close(); // a verification screen, or any other failure: never leave the window open
			throw e;
		}
	}

	synchronized boolean isOpen() {
		return context != null;
	}

	/** Also on app shutdown, so a window left open for the login never outlives the app. */
	@PreDestroy
	synchronized void close() {
		try {
			if (context != null) {
				context.close();
			}
		}
		catch (PlaywrightException e) {
			// already gone
		}
		try {
			if (playwright != null) {
				playwright.close();
			}
		}
		catch (PlaywrightException e) {
			// already gone
		}
		context = null;
		page = null;
		playwright = null;
	}

	/** Started on first use only, on the calling (run) thread: Playwright objects stay on one thread. */
	private void open() {
		if (context != null) {
			return;
		}
		playwright = Playwright.create();
		BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
				.setHeadless(headless);
		if (!headless) {
			// a maximised window whose page follows its real size, so the user's zoom-out shows more ads per scroll
			options.setViewportSize(null).setArgs(List.of("--start-maximized"));
		}
		context = playwright.chromium().launchPersistentContext(profile, options);
		page = context.pages().isEmpty() ? context.newPage() : context.pages().getFirst();
	}

	/** URL plus specific DOM: logged-in pages contain the words "checkpoint" and "captcha" in their scripts. */
	private void guard() {
		String path = path();
		if (path.startsWith("/checkpoint") || path.startsWith("/two_step_verification") || path.contains("captcha")
				|| page.locator(VERIFICATION_DOM).count() > 0) {
			throw new SourceException(VERIFICATION_PT);
		}
	}

	private boolean onLogin() {
		return path().startsWith("/login") || page.locator("input[name='pass']").count() > 0;
	}

	private String path() {
		return Objects.requireNonNullElse(URI.create(page.url()).getPath(), "");
	}
}
