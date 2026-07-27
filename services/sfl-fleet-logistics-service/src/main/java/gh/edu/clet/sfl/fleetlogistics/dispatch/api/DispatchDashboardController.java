package gh.edu.clet.sfl.fleetlogistics.dispatch.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchDashboardService;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.FleetActorResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** S171 dispatch operational dashboard (in-transit, open exceptions, custody gaps, variances, freshness). */
@RestController
@RequestMapping("/api/v1/dispatch/dashboard")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Dispatch Dashboards and Reports")
public class DispatchDashboardController {

    private final DispatchDashboardService service;
    private final FleetActorResolver actors;

    public DispatchDashboardController(DispatchDashboardService service, FleetActorResolver actors) {
        this.service = service;
        this.actors = actors;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> dashboard(@RequestParam String siteCode, HttpServletRequest h) {
        return ApiResponse.ok(service.dashboard(siteCode, actors.resolve(h)));
    }
}
