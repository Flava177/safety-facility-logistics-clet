package gh.edu.clet.sfl.common.web.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Throttles requests to the configured path patterns by remote address, at
 * {@code sfl.rate-limit.requests-per-window} calls per {@code sfl.rate-limit.window-seconds}. A caller
 * past the limit gets a bare {@code 429} with no body - the same information a client needs either
 * way, without echoing back anything request-derived.
 *
 * <p>Keyed on {@link HttpServletRequest#getRemoteAddr()}, not a {@code X-Forwarded-For} header: that
 * header is caller-supplied and trusting it here would let a caller pick its own rate-limit bucket.
 * A deployment that terminates behind a trusted reverse proxy should have the proxy set the socket
 * peer address correctly (or this filter adapted) rather than have this filter trust the header itself.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final FixedWindowRateLimiter limiter;
    private final List<String> pathPatterns;
    private final List<String> excludePathPatterns;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public RateLimitFilter(RateLimitProperties properties) {
        this.limiter = new FixedWindowRateLimiter(properties.getRequestsPerWindow(),
                properties.getWindowSeconds() * 1000L);
        this.pathPatterns = properties.getPathPatterns();
        this.excludePathPatterns = properties.getExcludePathPatterns();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!isThrottled(request.getServletPath())) {
            chain.doFilter(request, response);
            return;
        }
        String key = request.getRemoteAddr() + ":" + request.getServletPath();
        if (limiter.tryAcquire(key)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
    }

    private boolean isThrottled(String path) {
        boolean matched = pathPatterns.stream().anyMatch(pattern -> matcher.match(pattern, path));
        boolean excluded = excludePathPatterns.stream().anyMatch(pattern -> matcher.match(pattern, path));
        return matched && !excluded;
    }
}
