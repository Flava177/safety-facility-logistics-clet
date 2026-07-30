package gh.edu.clet.sfl.facilities.readiness.api;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.masterdata.api.FacilitiesResponses.RoomResponse;
import gh.edu.clet.sfl.facilities.readiness.application.ReadinessApplicationService;
import gh.edu.clet.sfl.facilities.readiness.application.ReadinessCommands;
import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSeverity;
import gh.edu.clet.sfl.facilities.shared.api.FacilitiesActorResolver;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The S152 readiness endpoints (SRS-SFL-S152-01, -02, -05).
 *
 * <p>The rule these serve: a space cannot be READY while a critical blocker is open. Submitting an
 * assessment derives the status; resolving the last critical blocker is what lets it become READY
 * again.
 */
@RestController
@RequestMapping("/api/v1/facilities/readiness")
@Tag(name = "S152 Readiness", description = "Checklists, assessments, blockers and examination locks")
public class ReadinessController {

    private final ReadinessApplicationService service;
    private final FacilitiesActorResolver actorResolver;

    public ReadinessController(ReadinessApplicationService service, FacilitiesActorResolver actorResolver) {
        this.service = service;
        this.actorResolver = actorResolver;
    }

    // ---- checklists ---------------------------------------------------------------------------

    @PostMapping("/checklists")
    @Operation(summary = "Create a readiness checklist",
            description = "NFR 23.8. Applicability is by space type and operating mode; both null means any.")
    public ResponseEntity<ReadinessResponses.ChecklistResponse> createChecklist(
            @Valid @RequestBody ReadinessRequests.CreateChecklist request, HttpServletRequest http) {
        ReadinessResponses.ChecklistResponse result = ReadinessResponses.ChecklistResponse.from(
                service.createChecklist(new ReadinessCommands.CreateChecklist(request.siteCode(),
                        request.checklistCode(), request.name(), request.description(), request.spaceType(),
                        request.operatingMode(), toItems(request.items()), actor(http), channel(http),
                        idempotencyKey(http))));
        return ResponseEntity.created(URI.create("/api/v1/facilities/readiness/checklists/" + result.id()))
                .body(result);
    }

    @GetMapping("/checklists")
    @Operation(summary = "List readiness checklists, optionally for one site")
    public List<ReadinessResponses.ChecklistResponse> checklists(
            @RequestParam(required = false) String siteCode, HttpServletRequest http) {
        return service.checklists(siteCode, actor(http), channel(http)).stream()
                .map(ReadinessResponses.ChecklistResponse::from).toList();
    }

    @GetMapping("/checklists/{checklistId}")
    @Operation(summary = "Read one readiness checklist and its questions")
    public ReadinessResponses.ChecklistResponse checklist(@PathVariable UUID checklistId,
            HttpServletRequest http) {
        return ReadinessResponses.ChecklistResponse.from(service.checklist(checklistId, actor(http),
                channel(http)));
    }

    @PatchMapping("/checklists/{checklistId}")
    @Operation(summary = "Update a readiness checklist",
            description = "Supplying items replaces them all and bumps the version, so past assessments stay "
                    + "readable against the questions they were taken against.")
    public ReadinessResponses.ChecklistResponse updateChecklist(@PathVariable UUID checklistId,
            @Valid @RequestBody ReadinessRequests.UpdateChecklist request, HttpServletRequest http) {
        return ReadinessResponses.ChecklistResponse.from(service.updateChecklist(
                new ReadinessCommands.UpdateChecklist(checklistId, request.name(), request.description(),
                        toItems(request.items()), request.expectedVersion(), actor(http), channel(http))));
    }

    // ---- assessments --------------------------------------------------------------------------

    @PostMapping("/assessments")
    @Operation(summary = "Submit a readiness assessment",
            description = "Raises a blocker for each failed item at the item's declared severity, then "
                    + "re-derives the space's readiness. Unanswered items count as failed.")
    public ResponseEntity<ReadinessResponses.AssessmentResponse> submitAssessment(
            @Valid @RequestBody ReadinessRequests.SubmitAssessment request, HttpServletRequest http) {
        List<ReadinessCommands.AssessmentAnswer> answers = request.answers() == null
                ? List.of()
                : request.answers().stream()
                        .map(answer -> new ReadinessCommands.AssessmentAnswer(answer.itemCode(),
                                answer.passed(), answer.comment()))
                        .toList();
        ReadinessResponses.AssessmentResponse result = ReadinessResponses.AssessmentResponse.from(
                service.submitAssessment(new ReadinessCommands.SubmitAssessment(request.roomId(),
                        request.checklistId(), answers, request.notes(), actor(http), channel(http),
                        idempotencyKey(http))));
        return ResponseEntity.created(URI.create("/api/v1/facilities/readiness/assessments/" + result.id()))
                .body(result);
    }

    @GetMapping("/assessments")
    @Operation(summary = "List readiness assessments, most recent first")
    public List<ReadinessResponses.AssessmentResponse> assessments(
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) UUID roomId,
            @RequestParam(defaultValue = "50") int limit,
            HttpServletRequest http) {
        return service.assessments(siteCode, roomId, limit, actor(http), channel(http)).stream()
                .map(ReadinessResponses.AssessmentResponse::from).toList();
    }

    @GetMapping("/assessments/{assessmentId}")
    @Operation(summary = "Read one readiness assessment with every answer")
    public ReadinessResponses.AssessmentResponse assessment(@PathVariable UUID assessmentId,
            HttpServletRequest http) {
        return ReadinessResponses.AssessmentResponse.from(service.assessment(assessmentId, actor(http),
                channel(http)));
    }

    // ---- blockers -----------------------------------------------------------------------------

    @PostMapping("/blockers")
    @Operation(summary = "Raise a readiness blocker by hand",
            description = "For what an officer saw that no checklist item covers.")
    public ResponseEntity<ReadinessResponses.BlockerResponse> raiseBlocker(
            @Valid @RequestBody ReadinessRequests.RaiseBlocker request, HttpServletRequest http) {
        ReadinessResponses.BlockerResponse result = ReadinessResponses.BlockerResponse.from(
                service.raiseBlocker(new ReadinessCommands.RaiseBlocker(request.roomId(), request.severity(),
                        request.description(), actor(http), channel(http))));
        return ResponseEntity.created(URI.create("/api/v1/facilities/readiness/blockers/" + result.id()))
                .body(result);
    }

    @GetMapping("/blockers")
    @Operation(summary = "List readiness blockers by site, space, severity and open state")
    public List<ReadinessResponses.BlockerResponse> blockers(
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) UUID roomId,
            @RequestParam(required = false) BlockerSeverity severity,
            @RequestParam(required = false) Boolean open,
            @RequestParam(defaultValue = "100") int limit,
            HttpServletRequest http) {
        return service.blockers(siteCode, roomId, severity, open, limit, actor(http), channel(http)).stream()
                .map(ReadinessResponses.BlockerResponse::from).toList();
    }

    @PatchMapping("/blockers/{blockerId}/resolution")
    @Operation(summary = "Resolve a readiness blocker",
            description = "A resolution note is required. Re-derives the space's readiness, which is how a "
                    + "space becomes READY again once the last critical blocker closes.")
    public ReadinessResponses.BlockerResponse resolveBlocker(@PathVariable UUID blockerId,
            @Valid @RequestBody ReadinessRequests.ResolveBlocker request, HttpServletRequest http) {
        return ReadinessResponses.BlockerResponse.from(service.resolveBlocker(
                new ReadinessCommands.ResolveBlocker(blockerId, request.resolutionNotes(), actor(http),
                        channel(http))));
    }

    // ---- current readiness and locks ----------------------------------------------------------

    @GetMapping("/rooms/{roomId}")
    @Operation(summary = "Read a space's current readiness and the reasons for it",
            description = "SRS-SFL-S152-05 drilldown: the status, the score and every open blocker.")
    public ReadinessResponses.OutcomeResponse roomReadiness(@PathVariable UUID roomId, HttpServletRequest http) {
        // Authorised through the blocker read, which is the data this returns.
        service.blockers(null, roomId, null, true, 1, actor(http), channel(http));
        return ReadinessResponses.OutcomeResponse.from(service.evaluate(roomId));
    }

    @PostMapping("/rooms/{roomId}/lock")
    @Operation(summary = "Engage a space's examination readiness lock",
            description = "NFR 23.3. While locked, attribute and lifecycle changes are refused without "
                    + "FACILITIES_READINESS_OVERRIDE.")
    public RoomResponse lock(@PathVariable UUID roomId,
            @Valid @RequestBody(required = false) ReadinessRequests.ReadinessLock request,
            HttpServletRequest http) {
        String reason = request == null ? null : request.reason();
        return RoomResponse.from(service.lockReadiness(new ReadinessCommands.LockReadiness(roomId, reason,
                actor(http), channel(http))));
    }

    @DeleteMapping("/rooms/{roomId}/lock")
    @Operation(summary = "Release a space's examination readiness lock")
    public RoomResponse unlock(@PathVariable UUID roomId,
            @RequestParam(required = false) String reason, HttpServletRequest http) {
        return RoomResponse.from(service.unlockReadiness(new ReadinessCommands.UnlockReadiness(roomId, reason,
                actor(http), channel(http))));
    }

    private static List<ReadinessCommands.ChecklistItem> toItems(List<ReadinessRequests.ChecklistItem> items) {
        return items == null
                ? null
                : items.stream()
                        .map(item -> new ReadinessCommands.ChecklistItem(item.itemCode(), item.description(),
                                item.severityIfFailed(), item.mandatory(), item.weight(), item.sortOrder()))
                        .toList();
    }

    private ActorContext actor(HttpServletRequest http) {
        return actorResolver.resolve(http);
    }

    private SourceChannel channel(HttpServletRequest http) {
        return actorResolver.resolveSourceChannel(http);
    }

    private String idempotencyKey(HttpServletRequest http) {
        return actorResolver.resolveIdempotencyKey(http);
    }
}
