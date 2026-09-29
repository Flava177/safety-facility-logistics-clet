package gh.edu.clet.sfl.facilities.spaceplanning.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.spaceplanning.application.SpacePlanningDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The S158 dashboard read: "Current versus planned space utilisation, allocation-scenario comparison,
 * space-change request pipeline, occupancy-standard compliance by unit."
 */
@RestController
@RequestMapping("/api/v1/facilities/space-planning/dashboard")
@Tag(name = "S158 Space Planning", description = "Utilisation, scenario comparison, pipeline and compliance")
public class SpacePlanningDashboardController {

    private final SpacePlanningDashboardService service;

    public SpacePlanningDashboardController(SpacePlanningDashboardService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "The S158 dashboard for a site")
    public ApiResponse<SpacePlanningResponses.DashboardResponse> dashboard(@RequestParam String siteCode,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(SpacePlanningResponses.DashboardResponse.from(
                service.dashboard(siteCode, actor, channel)));
    }
}
