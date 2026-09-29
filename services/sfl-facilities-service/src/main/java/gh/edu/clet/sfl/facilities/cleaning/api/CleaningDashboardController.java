package gh.edu.clet.sfl.facilities.cleaning.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.cleaning.application.CleaningDashboardService;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The S169 dashboard read: "scheduled versus completed tasks by site, overdue reactive requests, vendor
 * SLA compliance, occupant-feedback trend" (SRS §3.1, S169 Dashboard attribute).
 */
@RestController
@RequestMapping("/api/v1/facilities/cleaning/dashboard")
@Tag(name = "S169 Cleaning Dashboard", description = "Scheduled vs completed, overdue requests, SLA compliance, feedback trend")
public class CleaningDashboardController {

    private final CleaningDashboardService dashboard;

    public CleaningDashboardController(CleaningDashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping
    @Operation(summary = "The S169 dashboard, by site")
    public ApiResponse<CleaningResponses.DashboardResponse> dashboard(@RequestParam(required = false) String siteCode,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(CleaningResponses.DashboardResponse.from(
                dashboard.dashboard(siteCode, from, to, actor, channel)));
    }
}
