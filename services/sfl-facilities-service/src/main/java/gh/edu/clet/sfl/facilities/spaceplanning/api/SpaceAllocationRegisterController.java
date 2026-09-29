package gh.edu.clet.sfl.facilities.spaceplanning.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.spaceplanning.application.SpaceScenarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read of the S152 current-state allocation register, through S158's view of it - SRS-SFL-S158-01.
 *
 * <p>A separate controller from {@link SpaceScenarioController} because the register belongs to S152, not
 * to a scenario: this is "what is true now", the scenario endpoints are "what if". A draft {@code scenarioId}
 * is refused with {@code SPACE_SCENARIO_UNCOMMITTED} rather than answered.
 */
@RestController
@RequestMapping("/api/v1/facilities/space-planning/allocations")
@Tag(name = "S158 Space Planning", description = "The S152 current-state allocation register")
public class SpaceAllocationRegisterController {

    private final SpaceScenarioService service;

    public SpaceAllocationRegisterController(SpaceScenarioService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "The current allocation register, or one committed scenario's contribution to it")
    public ApiResponse<List<SpacePlanningResponses.AllocationResponse>> current(@RequestParam String siteCode,
            @RequestParam(required = false) UUID scenarioId, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(service.currentAllocations(siteCode, scenarioId, actor, channel).stream()
                .map(SpacePlanningResponses.AllocationResponse::from).toList());
    }
}
