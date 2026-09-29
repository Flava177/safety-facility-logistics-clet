package gh.edu.clet.sfl.facilities.spaceplanning.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.spaceplanning.application.SpaceChangeRequestService;
import gh.edu.clet.sfl.facilities.spaceplanning.application.SpacePlanningCommands;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.SpaceChangeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Space-change requests - SRS-SFL-S158-04. Submit, decide, action, resolve. */
@RestController
@RequestMapping("/api/v1/facilities/space-planning/requests")
@Tag(name = "S158 Space Planning", description = "Space-change request pipeline")
public class SpaceChangeRequestController {

    private final SpaceChangeRequestService service;

    public SpaceChangeRequestController(SpaceChangeRequestService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Submit a space-change request",
            description = "Requesting unit, justification, target space or area, urgency. Requires "
                    + "FACILITIES_SPACE_CHANGE_REQUEST; the requester sees only their own afterwards.")
    public ApiResponse<SpacePlanningResponses.SpaceChangeRequestResponse> submit(
            @Valid @RequestBody SpacePlanningRequests.SubmitRequest request, ActorContext actor,
            SourceChannel channel) {
        return respond(service.submit(new SpacePlanningCommands.SubmitRequest(request.siteCode(),
                request.requestingUnit(), request.justification(), request.targetRoomId(),
                request.targetAreaDescription(), request.requiredHeadcount(), request.urgency(), actor, channel)));
    }

    @PatchMapping("/{requestId}/decision")
    @Operation(summary = "Approve or decline a request",
            description = "Requires FACILITIES_SPACE_CHANGE_DECIDE and must not be the submitter.")
    public ApiResponse<SpacePlanningResponses.SpaceChangeRequestResponse> decide(@PathVariable UUID requestId,
            @Valid @RequestBody SpacePlanningRequests.DecideRequest request, ActorContext actor,
            SourceChannel channel) {
        return respond(service.decide(new SpacePlanningCommands.DecideRequest(requestId, request.approve(),
                request.reason(), request.expectedVersion(), actor, channel)));
    }

    @PostMapping("/{requestId}/link-scenario")
    @Operation(summary = "Meet an approved request with a like-for-like scenario",
            description = "Links both ways; the scenario must still be committed to resolve the request with it.")
    public ApiResponse<SpacePlanningResponses.SpaceChangeRequestResponse> linkScenario(
            @PathVariable UUID requestId, @Valid @RequestBody SpacePlanningRequests.LinkScenario request,
            ActorContext actor, SourceChannel channel) {
        return respond(service.linkScenario(
                new SpacePlanningCommands.LinkScenario(requestId, request.scenarioId(), actor, channel)));
    }

    @PostMapping("/{requestId}/hand-to-construction")
    @Operation(summary = "Hand an approved request needing physical works to S176",
            description = "Creates an S176 project reference and links it back to the request (S158-04 AC).")
    public ApiResponse<SpacePlanningResponses.SpaceChangeRequestResponse> handToConstruction(
            @PathVariable UUID requestId, @Valid @RequestBody SpacePlanningRequests.HandToConstruction request,
            ActorContext actor, SourceChannel channel) {
        return respond(service.handToConstruction(new SpacePlanningCommands.HandToConstruction(requestId,
                request.title(), request.scope(), request.roomIdsOrEmpty(), actor, channel)));
    }

    @PostMapping("/{requestId}/resolve")
    @Operation(summary = "Resolve a request",
            description = "Refused with SPACE_CHANGE_UNLINKED_RESOLUTION without a linked scenario or project.")
    public ApiResponse<SpacePlanningResponses.SpaceChangeRequestResponse> resolve(@PathVariable UUID requestId,
            @Valid @RequestBody SpacePlanningRequests.ResolveRequest request, ActorContext actor,
            SourceChannel channel) {
        return respond(service.resolve(new SpacePlanningCommands.ResolveRequest(requestId, request.note(),
                request.expectedVersion(), actor, channel)));
    }

    @GetMapping
    @Operation(summary = "Search the request pipeline",
            description = "A requester with no planning or decision authority sees only requests they submitted.")
    public ApiResponse<List<SpacePlanningResponses.SpaceChangeRequestResponse>> search(
            @RequestParam String siteCode, @RequestParam(required = false) SpaceChangeRequest.Status status,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(service.search(siteCode, status, actor, channel).stream()
                .map(SpacePlanningResponses.SpaceChangeRequestResponse::from).toList());
    }

    @GetMapping("/{requestId}")
    @Operation(summary = "Read one request")
    public ApiResponse<SpacePlanningResponses.SpaceChangeRequestResponse> find(@PathVariable UUID requestId,
            ActorContext actor, SourceChannel channel) {
        return respond(service.find(requestId, actor, channel));
    }

    private ApiResponse<SpacePlanningResponses.SpaceChangeRequestResponse> respond(SpaceChangeRequest request) {
        return ApiResponse.ok(SpacePlanningResponses.SpaceChangeRequestResponse.from(request));
    }
}
