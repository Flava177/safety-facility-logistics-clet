package gh.edu.clet.sfl.facilities.construction.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.construction.application.ConstructionDashboardService;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The S176 dashboard - pipeline by stage, milestone/variation exceptions, contractor compliance,
 * snagging backlog and handover completeness (SRS S176 system summary).
 */
@RestController
@RequestMapping("/api/v1/facilities/construction/dashboard")
@Tag(name = "S176 Dashboard", description = "Construction project pipeline, exceptions and compliance")
public class ConstructionDashboardController {

    private final ConstructionDashboardService service;

    public ConstructionDashboardController(ConstructionDashboardService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "The S176 dashboard for a site (or every site the caller may see)")
    public ApiResponse<ConstructionDashboardService.Dashboard> dashboard(
            @RequestParam(required = false) String siteCode, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(service.dashboard(siteCode, actor, channel));
    }

    @GetMapping("/integrations")
    @Operation(summary = "What S176 is and is not connected to",
            description = "SRS CORR-07: never reports a dependency as integrated without evidence. "
                    + "S164, S160a/S160, S133, S136/S141 and S223 are all named honestly here.")
    public ApiResponse<List<ConstructionDashboardService.IntegrationStatus>> integrations(ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(service.integrations(actor, channel));
    }
}
