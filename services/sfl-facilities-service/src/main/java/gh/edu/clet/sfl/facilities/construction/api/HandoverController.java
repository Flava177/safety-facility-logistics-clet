package gh.edu.clet.sfl.facilities.construction.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.construction.application.ConstructionCommands;
import gh.edu.clet.sfl.facilities.construction.application.HandoverService;
import gh.edu.clet.sfl.facilities.construction.domain.ConstructionProject;
import gh.edu.clet.sfl.facilities.construction.domain.DefectItem;
import gh.edu.clet.sfl.facilities.construction.domain.Handover;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Practical completion, handover, defects liability and closure - SRS-SFL-S176-04. */
@RestController
@RequestMapping("/api/v1/facilities/construction/projects/{projectId}")
@Tag(name = "S176 Handover and Defects", description = "Practical completion, handover, defects liability and closure")
public class HandoverController {

    private final HandoverService service;

    public HandoverController(HandoverService service) {
        this.service = service;
    }

    @PatchMapping("/practical-completion")
    @Operation(summary = "Record practical completion")
    public ApiResponse<ConstructionResponses.ProjectResponse> recordPracticalCompletion(@PathVariable UUID projectId,
            @Valid @RequestBody ConstructionRequests.VersionedRequest request, ActorContext actor,
            SourceChannel channel) {
        return project(service.recordPracticalCompletion(new ConstructionCommands.RecordPracticalCompletion(
                projectId, request.expectedVersion(), actor, channel)));
    }

    @PostMapping("/handover")
    @Operation(summary = "Record handover",
            description = "SRS-SFL-S176-04 AC: applies the listed S152 register changes in the same "
                    + "transaction. A handover naming no change, or missing a space the project "
                    + "declared it would change, is refused with PROJECT_HANDOVER_INCOMPLETE and the "
                    + "flag is kept. Confirms a committed S158 scenario where the project carries one.")
    public ResponseEntity<ApiResponse<ConstructionResponses.HandoverResponse>> handover(@PathVariable UUID projectId,
            @Valid @RequestBody ConstructionRequests.RecordHandover request, ActorContext actor,
            SourceChannel channel) {
        List<ConstructionCommands.RoomChange> changes = request.roomChanges() == null ? List.of()
                : request.roomChanges().stream().map(HandoverController::toRoomChange).toList();
        Handover handover = service.handover(new ConstructionCommands.RecordHandover(projectId,
                request.handoverDate(), request.notes(), changes, request.expectedVersion(), actor, channel));
        return ResponseEntity.created(URI.create("/api/v1/facilities/construction/projects/" + projectId
                        + "/handovers/" + handover.id()))
                .body(ApiResponse.ok(ConstructionResponses.HandoverResponse.from(handover)));
    }

    @PatchMapping("/handover/scenario-confirmation")
    @Operation(summary = "Retry an unresolved S158 scenario confirmation")
    public ApiResponse<ConstructionResponses.HandoverResponse> retryScenarioConfirmation(@PathVariable UUID projectId,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(ConstructionResponses.HandoverResponse.from(
                service.retryScenarioConfirmation(new ConstructionCommands.RetryScenarioConfirmation(projectId, actor,
                        channel))));
    }

    @PostMapping("/defects")
    @Operation(summary = "Raise a defects-liability item",
            description = "SRS-SFL-S176-04: only during the defects-liability period, only against a "
                    + "contractor assigned to the project. Raises an S153 work order tagged "
                    + "CONSTRUCTION_DEFECT with the project and contractor as its origin reference.")
    public ResponseEntity<ApiResponse<ConstructionResponses.DefectResponse>> raiseDefect(@PathVariable UUID projectId,
            @Valid @RequestBody ConstructionRequests.RaiseDefect request, ActorContext actor, SourceChannel channel) {
        DefectItem defect = service.raiseDefect(new ConstructionCommands.RaiseDefect(projectId,
                request.contractorId(), request.description(), request.roomId(), request.locationCode(),
                request.priority(), actor, channel));
        return ResponseEntity.created(URI.create("/api/v1/facilities/construction/projects/" + projectId
                        + "/defects/" + defect.id()))
                .body(ApiResponse.ok(ConstructionResponses.DefectResponse.from(defect)));
    }

    @PatchMapping("/defects/{defectId}/deferral")
    @Operation(summary = "Defer a defect with a reason",
            description = "SRS-SFL-S176-04: the alternative to closure that still lets the project "
                    + "close - an unresolved defect with no deferral keeps it open.")
    public ApiResponse<ConstructionResponses.DefectResponse> deferDefect(@PathVariable UUID projectId,
            @PathVariable UUID defectId, @Valid @RequestBody ConstructionRequests.DeferDefect request,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(ConstructionResponses.DefectResponse.from(
                service.deferDefect(new ConstructionCommands.DeferDefect(defectId, request.reason(), actor, channel))));
    }

    @PatchMapping("/closure")
    @Operation(summary = "Close the project",
            description = "SRS-SFL-S176-04 AC: refused with PROJECT_DEFECTS_OPEN, naming every open "
                    + "item, until each defects-liability item is closed or deferred.")
    public ApiResponse<ConstructionResponses.ProjectResponse> close(@PathVariable UUID projectId,
            @Valid @RequestBody ConstructionRequests.VersionedRequest request, ActorContext actor,
            SourceChannel channel) {
        return project(service.close(new ConstructionCommands.CloseProject(projectId, request.expectedVersion(),
                actor, channel)));
    }

    @GetMapping("/handovers")
    @Operation(summary = "Every handover attempt for the project, including flagged-incomplete ones")
    public ApiResponse<List<ConstructionResponses.HandoverResponse>> handovers(@PathVariable UUID projectId,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(service.handovers(projectId, actor, channel).stream()
                .map(ConstructionResponses.HandoverResponse::from).toList());
    }

    @GetMapping("/defects")
    @Operation(summary = "Every defects-liability item for the project")
    public ApiResponse<List<ConstructionResponses.DefectResponse>> defects(@PathVariable UUID projectId,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(service.defects(projectId, actor, channel).stream()
                .map(ConstructionResponses.DefectResponse::from).toList());
    }

    private static ConstructionCommands.RoomChange toRoomChange(ConstructionRequests.RoomChangeRequest request) {
        return new ConstructionCommands.RoomChange(request.action() == ConstructionRequests.RoomChangeRequest.RoomAction.CREATE
                ? ConstructionCommands.RoomChange.Action.CREATE : ConstructionCommands.RoomChange.Action.UPDATE,
                request.roomId(), request.floorId(), request.roomCode(), request.name(), request.spaceType(),
                request.capacity(), request.areaSqm(), request.costCentre(), request.bookable(),
                request.examinationCapable());
    }

    private ApiResponse<ConstructionResponses.ProjectResponse> project(ConstructionProject project) {
        return ApiResponse.ok(ConstructionResponses.ProjectResponse.from(project));
    }
}
