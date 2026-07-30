package gh.edu.clet.sfl.facilities.dashboard.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.dashboard.application.FacilityDashboardService;
import gh.edu.clet.sfl.facilities.dashboard.domain.FacilityDashboard;
import gh.edu.clet.sfl.facilities.shared.api.FacilitiesActorResolver;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The S152-05 dashboard endpoints.
 *
 * <p>The summary needs {@code FACILITIES_DASHBOARD_READ}; every drilldown needs
 * {@code FACILITIES_DASHBOARD_DRILLDOWN} as well, which is the requirement's "Restricted Drilldown"
 * error state — a manager may see that eleven spaces are blocked without being entitled to see which.
 */
@RestController
@RequestMapping("/api/v1/facilities/dashboard")
@Tag(name = "S152 Dashboard", description = "Readiness, blockers, unavailable spaces and examination risk")
public class FacilityDashboardController {

    private final FacilityDashboardService service;
    private final FacilitiesActorResolver actorResolver;

    public FacilityDashboardController(FacilityDashboardService service,
            FacilitiesActorResolver actorResolver) {
        this.service = service;
        this.actorResolver = actorResolver;
    }

    @GetMapping
    @Operation(summary = "Facility readiness dashboard",
            description = "SRS-SFL-S152-05. Computed live from the source records, with a stale-data "
                    + "warning when readiness is older than the configured freshness threshold.")
    public ApiResponse<FacilityDashboard> dashboard(@RequestParam(required = false) String siteCode,
            HttpServletRequest http) {
        return ApiResponse.ok(service.dashboard(siteCode, actor(http), channel(http)));
    }

    @GetMapping("/blockers")
    @Operation(summary = "Open readiness blockers behind the dashboard counts, worst and oldest first")
    public ApiResponse<List<FacilityDashboard.ExceptionRow>> blockers(@RequestParam(required = false) String siteCode,
            HttpServletRequest http) {
        return ApiResponse.ok(service.blockerRows(siteCode, actor(http), channel(http)));
    }

    @GetMapping("/unavailable")
    @Operation(summary = "Bookable spaces that are not currently available")
    public ApiResponse<List<FacilityDashboard.ExceptionRow>> unavailable(@RequestParam(required = false) String siteCode,
            HttpServletRequest http) {
        return ApiResponse.ok(service.unavailableRows(siteCode, actor(http), channel(http)));
    }

    @GetMapping("/stale")
    @Operation(summary = "Spaces whose readiness is older than the configured threshold, or never assessed")
    public ApiResponse<List<FacilityDashboard.ExceptionRow>> stale(@RequestParam(required = false) String siteCode,
            HttpServletRequest http) {
        return ApiResponse.ok(service.staleRows(siteCode, actor(http), channel(http)));
    }

    private ActorContext actor(HttpServletRequest http) {
        return actorResolver.resolve(http);
    }

    private SourceChannel channel(HttpServletRequest http) {
        return actorResolver.resolveSourceChannel(http);
    }
}
