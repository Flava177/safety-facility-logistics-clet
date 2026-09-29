package gh.edu.clet.sfl.facilities.spaceplanning.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.spaceplanning.application.OccupancyStandardService;
import gh.edu.clet.sfl.facilities.spaceplanning.application.SpacePlanningCommands;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Occupancy standards and the two-step compliance override - SRS-SFL-S158-02. */
@RestController
@RequestMapping("/api/v1/facilities/space-planning")
@Tag(name = "S158 Space Planning", description = "Occupancy standards and compliance overrides")
public class OccupancyStandardController {

    private final OccupancyStandardService service;

    public OccupancyStandardController(OccupancyStandardService service) {
        this.service = service;
    }

    @PostMapping("/standards")
    @Operation(summary = "Define the next version of a space type's occupancy standard",
            description = "Supersedes the active version rather than editing it.")
    public ApiResponse<SpacePlanningResponses.StandardResponse> define(
            @Valid @RequestBody SpacePlanningRequests.DefineStandard request, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(SpacePlanningResponses.StandardResponse.from(
                service.define(new SpacePlanningCommands.DefineStandard(request.siteCode(), request.spaceType(),
                        request.maxCapacityPercent(), request.minAreaPerPersonSqm(), request.note(), actor,
                        channel))));
    }

    @GetMapping("/standards")
    @Operation(summary = "Every occupancy standard version at a site")
    public ApiResponse<List<SpacePlanningResponses.StandardResponse>> standards(@RequestParam String siteCode,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(service.standards(siteCode, actor, channel).stream()
                .map(SpacePlanningResponses.StandardResponse::from).toList());
    }

    @PostMapping("/scenarios/{scenarioId}/overrides")
    @Operation(summary = "Request a compliance override for a non-compliant room in a scenario",
            description = "First step of the two-person rule: the requester's reason. Refused with "
                    + "SPACE_OVERRIDE_INCOMPLETE on approval by the same person.")
    public ApiResponse<SpacePlanningResponses.OverrideResponse> requestOverride(@PathVariable UUID scenarioId,
            @Valid @RequestBody SpacePlanningRequests.RequestOverride request, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(SpacePlanningResponses.OverrideResponse.from(
                service.requestOverride(new SpacePlanningCommands.RequestOverride(scenarioId, request.roomId(),
                        request.reason(), actor, channel))));
    }

    @PostMapping("/overrides/{overrideId}/approve")
    @Operation(summary = "Approve a pending override",
            description = "Requires FACILITIES_OCCUPANCY_OVERRIDE_APPROVE and a different person than the requester.")
    public ApiResponse<SpacePlanningResponses.OverrideResponse> approve(@PathVariable UUID overrideId,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(SpacePlanningResponses.OverrideResponse.from(
                service.approveOverride(new SpacePlanningCommands.ApproveOverride(overrideId, actor, channel))));
    }

    @GetMapping("/scenarios/{scenarioId}/overrides")
    @Operation(summary = "Overrides recorded against a scenario")
    public ApiResponse<List<SpacePlanningResponses.OverrideResponse>> overrides(@PathVariable UUID scenarioId,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(service.overrides(scenarioId, actor, channel).stream()
                .map(SpacePlanningResponses.OverrideResponse::from).toList());
    }
}
