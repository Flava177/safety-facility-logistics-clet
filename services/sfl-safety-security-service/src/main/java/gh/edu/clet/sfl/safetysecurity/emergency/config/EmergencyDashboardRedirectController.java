package gh.edu.clet.sfl.safetysecurity.emergency.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Sends any dashboard URL typed against this port to the dashboard.
 *
 * <h2>The dead end this removes</h2>
 *
 * <p>Only {@code sfl-fleet-logistics-service} packages the dashboard bundle, so {@code /ui} on 8092
 * has never existed. Typing {@code localhost:8092/ui/login} - which is the obvious thing to try when
 * three services are running and you want the one you were just looking at - produced Spring's
 * Whitelabel Error Page: "no explicit mapping for /error", status 404. Nothing was broken, and it
 * read exactly like something was.
 *
 * <p>The whole path is carried across, so {@code 8092/ui/login} lands on {@code 8093/ui/login} and
 * {@code 8092/ui/emergency/activations} lands on the activations screen, rather than dumping
 * everybody at a home page and making them navigate back to where they were going.
 *
 * <h2>Why a controller and not a view controller</h2>
 *
 * <p>{@code ViewControllerRegistry} redirects a fixed path to a fixed target; it cannot carry the
 * remainder of a wildcard through. That is the only reason this is code - the root redirect next
 * door stays declarative because it has nothing to carry.
 *
 * <p>302, not 301: the target moves behind a gateway, and a permanent redirect would be cached by
 * browsers long after the deployment topology changed.
 */
@Controller
class EmergencyDashboardRedirectController {

    /** This deployable's namespace in the dashboard. SSEMP screens live under it. */
    private static final String PLATFORM = "/safetysecurity";

    private final String dashboardBaseUrl;

    EmergencyDashboardRedirectController(
            @Value("${sfl.dashboard.base-url:http://localhost:8093/ui}") String dashboardBaseUrl) {
        this.dashboardBaseUrl = dashboardBaseUrl.replaceAll("/+$", "");
    }

    @GetMapping({"/ui", "/ui/**"})
    RedirectView toDashboard(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Everything after "/ui" - which is three characters, so the separating slash is kept.
        // Taking four dropped it and produced http://localhost:8093/uilogin, a 302 to nowhere.
        String remainder = path.length() > 3 ? path.substring(3) : "";
        String query = request.getQueryString();
        return new RedirectView(dashboardBaseUrl + PLATFORM + remainder + (query == null ? "" : "?" + query));
    }
}
