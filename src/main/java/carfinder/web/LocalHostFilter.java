package carfinder.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Answers 403 unless the Host is localhost or 127.0.0.1 with the app's port. Stops DNS rebinding: a web page whose own
 * name resolves to 127.0.0.1 still sends that name as the Host. Every path is checked: "/%61pi/data" also reaches the
 * API.
 */
@Component
class LocalHostFilter extends OncePerRequestFilter {

	private final Set<String> allowed;

	LocalHostFilter(@Value("${server.port}") int port) {
		this.allowed = Set.of("localhost:" + port, "127.0.0.1:" + port);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String host = request.getHeader("Host");
		if (host == null || !allowed.contains(host.toLowerCase(Locale.ROOT))) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		chain.doFilter(request, response);
	}
}
