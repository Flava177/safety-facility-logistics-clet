package gh.edu.clet.sfl.facilities.buildingsystems.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.buildingsystems.application.BuildingHealthService;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Building system health - SRS-SFL-S156-03.
 *
 * <p>Computed on read, never stored: see {@link BuildingHealthService}. Carries the S156 procurement-gate
 * status (CORR-07) so nothing built on this response can describe the feed as integrated.
 */
@RestController
@RequestMapping("/api/v1/facilities/building-systems")
@Tag(name = "S156 Health", description = "Per-site/building/system health rollup, never defaulting to healthy")
public class BuildingHealthController {

    private final BuildingHealthService health;

    public BuildingHealthController(BuildingHealthService health) {
        this.health = health;
    }

    @GetMapping("/health")
    @Operation(summary = "Building system health for a site",
            description = "SRS-SFL-S156-03. A device with no reading within its expected interval shows "
                    + "UNKNOWN, never NORMAL by default.")
    public ApiResponse<BuildingSystemsResponses.SiteHealthResponse> health(@RequestParam String siteCode,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(BuildingSystemsResponses.SiteHealthResponse.from(health.health(siteCode, actor, channel)));
    }
}
