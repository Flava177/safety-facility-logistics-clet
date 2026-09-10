package gh.edu.clet.sfl.common.web.ratelimit;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code sfl.rate-limit.*} - off by default (see {@link RateLimitAutoConfiguration}); a service
 * turns it on for a production profile with {@code sfl.rate-limit.enabled=true}.
 */
@ConfigurationProperties(prefix = "sfl.rate-limit")
public class RateLimitProperties {

    private boolean enabled = false;
    private int requestsPerWindow = 300;
    private int windowSeconds = 60;
    private List<String> pathPatterns = List.of("/api/**");
    private List<String> excludePathPatterns = List.of(
            "/actuator/**", "/api/v1/system/info",
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
            "/api/v1/*/integrations/webhooks/**", "/api/v1/emergency/provider-callbacks/**");

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRequestsPerWindow() {
        return requestsPerWindow;
    }

    public void setRequestsPerWindow(int requestsPerWindow) {
        this.requestsPerWindow = requestsPerWindow;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public List<String> getPathPatterns() {
        return pathPatterns;
    }

    public void setPathPatterns(List<String> pathPatterns) {
        this.pathPatterns = pathPatterns == null ? List.of() : List.copyOf(pathPatterns);
    }

    public List<String> getExcludePathPatterns() {
        return excludePathPatterns;
    }

    public void setExcludePathPatterns(List<String> excludePathPatterns) {
        this.excludePathPatterns = excludePathPatterns == null ? List.of() : List.copyOf(excludePathPatterns);
    }
}
