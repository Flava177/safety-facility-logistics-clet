package gh.edu.clet.sfl.fleetlogistics.fleet.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetDashboardApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetDashboardApplicationService.DashboardDrilldownRow;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetDashboardApplicationService.DashboardFilter;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetDashboardApplicationService.DashboardReconciliation;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetDashboardApplicationService.GoLiveReadinessReport;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetDashboardApplicationService.OperationsDashboardSnapshot;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowPriority;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Operations dashboard and reporting endpoints (SRS-SFL-S166-05). */
@RestController
@RequestMapping("/api/v1/fleet")
class FleetDashboardController {

    private final FleetDashboardApplicationService dashboards;
    private final FleetActorResolver actorResolver;

    FleetDashboardController(FleetDashboardApplicationService dashboards, FleetActorResolver actorResolver) {
        this.dashboards = dashboards;
        this.actorResolver = actorResolver;
    }

    @GetMapping("/dashboards/operations")
    ApiResponse<OperationsDashboardSnapshot> operations(
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowStatus status,
            @RequestParam(required = false) WorkflowPriority priority,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) OperatingMode operatingMode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "false") boolean requireFresh,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(dashboards.operations(new DashboardFilter(siteCode, status, priority, owner,
                operatingMode, from, to), actorResolver.resolve(httpRequest), requireFresh));
    }

    @GetMapping("/dashboards/operations/drilldowns/{indicator}")
    ApiResponse<List<DashboardDrilldownRow>> drilldown(@PathVariable String indicator,
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowStatus status,
            @RequestParam(required = false) WorkflowPriority priority,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) OperatingMode operatingMode,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(dashboards.drilldown(indicator,
                new DashboardFilter(siteCode, status, priority, owner, operatingMode, null, null),
                actorResolver.resolve(httpRequest)));
    }

    @GetMapping("/dashboards/operations/reconciliation")
    ApiResponse<DashboardReconciliation> reconciliation(@RequestParam(required = false) String siteCode,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(dashboards.operations(new DashboardFilter(siteCode, null, null, null, null,
                null, null), actorResolver.resolve(httpRequest), false).reconciliation());
    }

    @GetMapping("/reports/go-live-readiness")
    ApiResponse<GoLiveReadinessReport> goLiveReadiness(@RequestParam(required = false) String siteCode,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(dashboards.goLiveReadiness(new DashboardFilter(siteCode, null, null, null,
                null, null, null), actorResolver.resolve(httpRequest)));
    }
}
