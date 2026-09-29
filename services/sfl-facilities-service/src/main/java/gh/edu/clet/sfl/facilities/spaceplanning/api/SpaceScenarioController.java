package gh.edu.clet.sfl.facilities.spaceplanning.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.spaceplanning.application.SpacePlanningCommands;
import gh.edu.clet.sfl.facilities.spaceplanning.application.SpaceScenarioService;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.AllocationScenario;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.ScenarioStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Allocation scenarios - SRS-SFL-S158-01. Model, compare, commit; the S152 register they read and, on
 * commit, may write.
 */
@RestController
@RequestMapping("/api/v1/facilities/space-planning/scenarios")
@Tag(name = "S158 Space Planning", description = "Allocation scenarios modelled against the S152 register")
public class SpaceScenarioController {

    private final SpaceScenarioService service;

    public SpaceScenarioController(SpaceScenarioService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create a scenario (draft)",
            description = "A versioned plan against S152's current register. Never changes S152 until committed.")
    public ApiResponse<SpacePlanningResponses.ScenarioResponse> create(
            @Valid @RequestBody SpacePlanningRequests.CreateScenario request, ActorContext actor,
            SourceChannel channel) {
        return respond(service.create(new SpacePlanningCommands.CreateScenario(request.siteCode(), request.name(),
                request.description(), request.spaceChangeRequestId(), actor, channel)));
    }

    @PostMapping("/{scenarioId}/revisions")
    @Operation(summary = "Revise a plan as a new draft version",
            description = "Copies the source version's allocations into a new draft; the source is unchanged.")
    public ApiResponse<SpacePlanningResponses.ScenarioResponse> revise(@PathVariable UUID scenarioId,
            @Valid @RequestBody SpacePlanningRequests.ReviseScenario request, ActorContext actor,
            SourceChannel channel) {
        return respond(service.revise(new SpacePlanningCommands.ReviseScenario(scenarioId, request.name(),
                request.description(), actor, channel)));
    }

    @PatchMapping("/{scenarioId}")
    @Operation(summary = "Rename or redescribe a draft")
    public ApiResponse<SpacePlanningResponses.ScenarioResponse> rename(@PathVariable UUID scenarioId,
            @Valid @RequestBody SpacePlanningRequests.RenameScenario request, ActorContext actor,
            SourceChannel channel) {
        return respond(service.rename(new SpacePlanningCommands.RenameScenario(scenarioId, request.name(),
                request.description(), request.expectedVersion(), actor, channel)));
    }

    @PutMapping("/{scenarioId}/rooms/{roomId}")
    @Operation(summary = "Set a room's allocation in the scenario",
            description = "An empty unit list vacates the room in this scenario. Compliance is computed and "
                    + "saved, flagged rather than refused if non-compliant (S158-02).")
    public ApiResponse<SpacePlanningResponses.RoomComplianceResponse> setRoomAllocation(
            @PathVariable UUID scenarioId, @PathVariable UUID roomId,
            @Valid @RequestBody SpacePlanningRequests.SetRoomAllocation request, ActorContext actor,
            SourceChannel channel) {
        SpaceScenarioService.RoomPlan plan = service.setRoomAllocation(new SpacePlanningCommands.SetRoomAllocation(
                scenarioId, roomId, request.toUnits(), request.expectedVersion(), actor, channel));
        return ApiResponse.ok(SpacePlanningResponses.RoomComplianceResponse.from(plan.compliance()));
    }

    @DeleteMapping("/{scenarioId}/rooms/{roomId}")
    @Operation(summary = "Remove a room from the scenario")
    public ApiResponse<SpacePlanningResponses.ScenarioResponse> removeRoom(@PathVariable UUID scenarioId,
            @PathVariable UUID roomId, @RequestParam(required = false) Long expectedVersion, ActorContext actor,
            SourceChannel channel) {
        return respond(service.removeRoom(
                new SpacePlanningCommands.RemoveRoom(scenarioId, roomId, expectedVersion, actor, channel)));
    }

    @PostMapping("/{scenarioId}/commit")
    @Operation(summary = "Commit the scenario - the explicit, audited, named action",
            description = "LIKE_FOR_LIKE applies to the S152 register in this call. PHYSICAL_WORKS proposes an "
                    + "S176 project and stays COMMITTED, awaiting S176's handover.")
    public ApiResponse<SpacePlanningResponses.CommitResultResponse> commit(@PathVariable UUID scenarioId,
            @Valid @RequestBody SpacePlanningRequests.CommitScenario request, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(SpacePlanningResponses.CommitResultResponse.from(
                service.commit(new SpacePlanningCommands.CommitScenario(scenarioId, request.outcome(),
                        request.note(), request.projectTitle(), request.projectScope(), request.expectedVersion(),
                        actor, channel))));
    }

    @PostMapping("/{scenarioId}/discard")
    @Operation(summary = "Discard a draft, with a reason")
    public ApiResponse<SpacePlanningResponses.ScenarioResponse> discard(@PathVariable UUID scenarioId,
            @Valid @RequestBody SpacePlanningRequests.DiscardScenario request, ActorContext actor,
            SourceChannel channel) {
        return respond(service.discard(new SpacePlanningCommands.DiscardScenario(scenarioId, request.reason(),
                request.expectedVersion(), actor, channel)));
    }

    @PostMapping("/{scenarioId}/apply")
    @Operation(summary = "Apply a committed scenario's allocations to the S152 register",
            description = "Refused with SPACE_SCENARIO_UNCOMMITTED on a draft - the 'Uncommitted Scenario "
                    + "Referenced' error state.")
    public ApiResponse<List<SpacePlanningResponses.AllocationResponse>> apply(@PathVariable UUID scenarioId,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(service.applyToRegister(scenarioId, actor, channel).stream()
                .map(SpacePlanningResponses.AllocationResponse::from).toList());
    }

    @GetMapping
    @Operation(summary = "Search scenarios for a site")
    public ApiResponse<List<SpacePlanningResponses.ScenarioResponse>> search(@RequestParam String siteCode,
            @RequestParam(required = false) ScenarioStatus status, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(service.search(siteCode, status, actor, channel).stream()
                .map(SpacePlanningResponses.ScenarioResponse::from).toList());
    }

    @GetMapping("/{scenarioId}")
    @Operation(summary = "Read one scenario")
    public ApiResponse<SpacePlanningResponses.ScenarioResponse> find(@PathVariable UUID scenarioId,
            ActorContext actor, SourceChannel channel) {
        return respond(service.find(scenarioId, actor, channel));
    }

    @GetMapping("/{scenarioId}/lines")
    @Operation(summary = "A scenario's allocation lines")
    public ApiResponse<List<SpacePlanningResponses.ScenarioLineResponse>> lines(@PathVariable UUID scenarioId,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(service.lines(scenarioId, actor, channel).stream()
                .map(SpacePlanningResponses.ScenarioLineResponse::from).toList());
    }

    @GetMapping("/{scenarioId}/compliance")
    @Operation(summary = "Per-room occupancy compliance in a scenario, computed now")
    public ApiResponse<List<SpacePlanningResponses.RoomComplianceResponse>> compliance(
            @PathVariable UUID scenarioId, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(service.compliance(scenarioId, actor, channel).stream()
                .map(SpacePlanningResponses.RoomComplianceResponse::from).toList());
    }

    @GetMapping("/compare")
    @Operation(summary = "Compare scenarios room by room",
            description = "Against the current S152 register and against each other. One to ten scenario ids.")
    public ApiResponse<SpacePlanningResponses.ComparisonResponse> compare(
            @RequestParam List<UUID> scenarioIds, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(SpacePlanningResponses.ComparisonResponse.from(
                service.compare(scenarioIds, actor, channel)));
    }

    private ApiResponse<SpacePlanningResponses.ScenarioResponse> respond(AllocationScenario scenario) {
        return ApiResponse.ok(SpacePlanningResponses.ScenarioResponse.from(scenario));
    }
}
