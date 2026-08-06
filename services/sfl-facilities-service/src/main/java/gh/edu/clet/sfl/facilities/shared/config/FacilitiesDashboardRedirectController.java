package gh.edu.clet.sfl.facilities.shared.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Sends any dashboard URL typed against this port to the dashboard.
 *
 * <p>The twin of the SSEMP controller, and here for the same reason: only
 * {@code sfl-fleet-logistics-service} packages the bundle, so {@code /ui} on 8091 has never existed
 * and typing it produced a Whitelabel 404 that reads like a broken service. {@code /} already
 * redirects to the facilities screens; this covers everything under {@code /ui}, carrying the rest of
 * the path so the caller arrives where they were going rather than at a home page.
 *
 * @see FacilitiesWebConfiguration the root redirect, which stays declarative because it carries nothing
 */
@Controller
class FacilitiesDashboardRedirectController {

    /** This deployable's namespace in the dashboard. IFIMP screens live under it. */
    private static final String PLATFORM = "/facilities";

    private final String dashboardBaseUrl;

    FacilitiesDashboardRedirectController(
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
