package gh.edu.clet.sfl.facilities.maintenance.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.maintenance.application.MaintenanceCommands;
import gh.edu.clet.sfl.facilities.maintenance.application.MaintenanceEvidenceService;
import gh.edu.clet.sfl.facilities.maintenance.application.WorkOrderApplicationService;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderStatus;
import gh.edu.clet.sfl.facilities.shared.api.FacilitiesActorResolver;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
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
 * The work-order workflow — SRS-SFL-S153-02, -03.
 *
 * <p>{@code /from-fault}, the assignment path and the closure path keep the spellings the pre-S152
 * service used, so existing callers and runbooks still resolve. Everything else is new: the four
 * transitions the old three-state model had nowhere for, cancellation, parts and evidence.
 *
 * <p>Idempotency keys are accepted on the two state-<em>creating</em> POSTs and nowhere else. A PATCH
 * transition is guarded by the record's version and its state machine, so a repeat is either a no-op
 * or an invalid-transition error, and a key would be ceremony with no failure mode behind it.
 */
@RestController
@RequestMapping("/api/v1/facilities/work-orders")
@Tag(name = "S153 Work orders", description = "Assignment, escalation, hold, closure, parts and evidence")
public class WorkOrderController {

    private final WorkOrderApplicationService service;
    private final MaintenanceEvidenceService evidence;
    private final FacilitiesActorResolver actorResolver;
    private final Clock clock;

    public WorkOrderController(WorkOrderApplicationService service, MaintenanceEvidenceService evidence,
            FacilitiesActorResolver actorResolver, Clock clock) {
        this.service = service;
        this.evidence = evidence;
        this.actorResolver = actorResolver;
        this.clock = clock;
    }

    // ---- lifecycle --------------------------------------------------------------------------

    @PostMapping("/from-fault")
    @Operation(summary = "Raise a work order against a reported fault",
            description = "SRS-SFL-S153-02. The SLA is computed from the site's configuration, its "
                    + "operating mode and — where a vendor is named — the contracted response time, "
                    + "whichever is tighter.")
    public ResponseEntity<ApiResponse<MaintenanceResponses.WorkOrderResponse>> createFromFault(
            @Valid @RequestBody MaintenanceRequests.CreateWorkOrder request, HttpServletRequest http) {
        MaintenanceResponses.WorkOrderResponse result = MaintenanceResponses.WorkOrderResponse.from(
                service.createFromFault(new MaintenanceCommands.CreateWorkOrderFromFault(
                        request.facilityFaultId(), request.vendorId(), request.assignTo(), actor(http),
                        channel(http), idempotencyKey(http), request)),
                clock);
        return ResponseEntity.created(URI.create("/api/v1/facilities/work-orders/" + result.id()))
                .body(ApiResponse.ok(result));
    }

    @PatchMapping("/{workOrderId}/assignment")
    @Operation(summary = "Assign or reassign a work order",
            description = "Reassignment is the same move. Assigning an order that is on hold releases "
                    + "the hold, because handing over blocked work is not an assignment anybody can act on.")
    public ApiResponse<MaintenanceResponses.WorkOrderResponse> assign(@PathVariable UUID workOrderId,
            @Valid @RequestBody MaintenanceRequests.AssignWorkOrder request, HttpServletRequest http) {
        return respond(service.assign(new MaintenanceCommands.AssignWorkOrder(workOrderId,
                request.assignedTo(), request.vendorId(), request.expectedVersion(), actor(http),
                channel(http))));
    }

    @PatchMapping("/{workOrderId}/start")
    @Operation(summary = "The assignee has started work")
    public ApiResponse<MaintenanceResponses.WorkOrderResponse> start(@PathVariable UUID workOrderId,
            @Valid @RequestBody MaintenanceRequests.TransitionWorkOrder request, HttpServletRequest http) {
        return transition(workOrderId, MaintenanceCommands.TransitionWorkOrder.Transition.START, request,
                http);
    }

    @PatchMapping("/{workOrderId}/hold")
    @Operation(summary = "Put a work order on hold",
            description = "For something outside the assignee's control — a part, an access window, a "
                    + "vendor. The reason is required. Time on hold is accumulated but does not stop "
                    + "the SLA clock: a hall is no less unusable because the reason is a supplier.")
    public ApiResponse<MaintenanceResponses.WorkOrderResponse> hold(@PathVariable UUID workOrderId,
            @Valid @RequestBody MaintenanceRequests.TransitionWorkOrder request, HttpServletRequest http) {
        return transition(workOrderId, MaintenanceCommands.TransitionWorkOrder.Transition.HOLD, request, http);
    }

    @PatchMapping("/{workOrderId}/completion")
    @Operation(summary = "The assignee says the work is done",
            description = "Not yet accepted. Closure is a separate, authorised act — see /closure.")
    public ApiResponse<MaintenanceResponses.WorkOrderResponse> complete(@PathVariable UUID workOrderId,
            @Valid @RequestBody MaintenanceRequests.TransitionWorkOrder request, HttpServletRequest http) {
        return transition(workOrderId, MaintenanceCommands.TransitionWorkOrder.Transition.COMPLETE, request,
                http);
    }

    @PatchMapping("/{workOrderId}/reopen")
    @Operation(summary = "Reject a completion and send the work back",
            description = "Takes the closing permission, not the updating one: reopening reverses "
                    + "somebody's judgement that the work was finished. SRS-SFL-S153-02.")
    public ApiResponse<MaintenanceResponses.WorkOrderResponse> reopen(@PathVariable UUID workOrderId,
            @Valid @RequestBody MaintenanceRequests.TransitionWorkOrder request, HttpServletRequest http) {
        return transition(workOrderId, MaintenanceCommands.TransitionWorkOrder.Transition.REOPEN, request,
                http);
    }

    @PatchMapping("/{workOrderId}/closure")
    @Operation(summary = "Accept and close a work order",
            description = "SRS-SFL-S153-02: refused without a closure reason, and without the evidence "
                    + "the configuration required when the order was raised. Closing resolves the fault "
                    + "behind it, and a preventive order records the service against its asset.")
    public ApiResponse<MaintenanceResponses.WorkOrderResponse> close(@PathVariable UUID workOrderId,
            @Valid @RequestBody MaintenanceRequests.CloseWorkOrder request, HttpServletRequest http) {
        return respond(service.close(new MaintenanceCommands.CloseWorkOrder(workOrderId,
                request.closureNotes(), request.expectedVersion(), actor(http), channel(http))));
    }

    @PatchMapping("/{workOrderId}/cancellation")
    @Operation(summary = "Abandon a work order, with a reason")
    public ApiResponse<MaintenanceResponses.WorkOrderResponse> cancel(@PathVariable UUID workOrderId,
            @Valid @RequestBody MaintenanceRequests.CancelWorkOrder request, HttpServletRequest http) {
        return respond(service.cancel(new MaintenanceCommands.CancelWorkOrder(workOrderId, request.reason(),
                request.expectedVersion(), actor(http), channel(http))));
    }

    // ---- queries ------------------------------------------------------------------------------

    @GetMapping
    @Operation(summary = "Search work orders",
            description = "A vendor technician sees only the orders assigned to them, whatever the "
                    + "filters say. Site scope is not a sufficient boundary for a contractor.")
    public ApiResponse<List<MaintenanceResponses.WorkOrderResponse>> search(
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) UUID roomId,
            @RequestParam(required = false) UUID assetId,
            @RequestParam(required = false) WorkOrderStatus status,
            @RequestParam(required = false) String assignedTo,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(required = false) Boolean openOnly,
            @RequestParam(defaultValue = "100") int limit,
            HttpServletRequest http) {
        return ApiResponse.ok(service.search(siteCode, roomId, assetId, status, assignedTo, vendorId,
                        openOnly, limit, actor(http), channel(http)).stream()
                .map(order -> MaintenanceResponses.WorkOrderResponse.from(order, clock))
                .toList());
    }

    @GetMapping("/{workOrderId}")
    @Operation(summary = "Read one work order")
    public ApiResponse<MaintenanceResponses.WorkOrderResponse> findById(@PathVariable UUID workOrderId,
            HttpServletRequest http) {
        return respond(service.findById(workOrderId, actor(http), channel(http)));
    }

    // ---- parts --------------------------------------------------------------------------------

    @GetMapping("/{workOrderId}/parts")
    @Operation(summary = "Parts consumed on a work order")
    public ApiResponse<List<MaintenanceResponses.PartResponse>> parts(@PathVariable UUID workOrderId,
            HttpServletRequest http) {
        return ApiResponse.ok(service.parts(workOrderId, actor(http), channel(http)).stream()
                .map(MaintenanceResponses.PartResponse::from)
                .toList());
    }

    @PostMapping("/{workOrderId}/parts")
    @Operation(summary = "Record a part fitted",
            description = "Not a stores system: what was fitted, how many and what it cost. Cost is "
                    + "optional, because a technician fitting from the van often does not know it.")
    public ResponseEntity<ApiResponse<MaintenanceResponses.PartResponse>> recordPart(
            @PathVariable UUID workOrderId, @Valid @RequestBody MaintenanceRequests.RecordPart request,
            HttpServletRequest http) {
        MaintenanceResponses.PartResponse result = MaintenanceResponses.PartResponse.from(
                service.recordPart(new MaintenanceCommands.RecordPart(workOrderId, request.partCode(),
                        request.description(), request.quantity(), request.unitCost(), request.currency(),
                        request.supplier(), actor(http), channel(http))));
        return ResponseEntity
                .created(URI.create("/api/v1/facilities/work-orders/" + workOrderId + "/parts/" + result.id()))
                .body(ApiResponse.ok(result));
    }

    @DeleteMapping("/{workOrderId}/parts/{partId}")
    @Operation(summary = "Remove a part recorded in error")
    public ApiResponse<Void> removePart(@PathVariable UUID workOrderId, @PathVariable UUID partId,
            HttpServletRequest http) {
        service.removePart(new MaintenanceCommands.RemovePart(workOrderId, partId, actor(http),
                channel(http)));
        return ApiResponse.ok(null);
    }

    // ---- evidence -----------------------------------------------------------------------------

    @GetMapping("/{workOrderId}/evidence")
    @Operation(summary = "Evidence attached to a work order")
    public ApiResponse<List<MaintenanceResponses.EvidenceResponse>> evidence(@PathVariable UUID workOrderId,
            HttpServletRequest http) {
        return ApiResponse.ok(evidence.forWorkOrder(workOrderId, actor(http), channel(http)).stream()
                .map(MaintenanceResponses.EvidenceResponse::from)
                .toList());
    }

    @PostMapping("/{workOrderId}/evidence")
    @Operation(summary = "Attach closure evidence by reference",
            description = "SRS-SFL-S153-03. The file lives in object storage; this records where it is, "
                    + "its SHA-256, who uploaded it and how long it must be kept. The retention class "
                    + "is mandatory.")
    public ResponseEntity<ApiResponse<MaintenanceResponses.EvidenceResponse>> attachEvidence(
            @PathVariable UUID workOrderId, @Valid @RequestBody MaintenanceRequests.AttachEvidence request,
            HttpServletRequest http) {
        MaintenanceResponses.EvidenceResponse result = MaintenanceResponses.EvidenceResponse.from(
                evidence.attach(new MaintenanceCommands.AttachEvidence(workOrderId, request.evidenceType(),
                        request.fileReference(), request.fileName(), request.mediaType(), request.sizeBytes(),
                        request.contentHash(), request.retentionClass(), request.notes(), actor(http),
                        channel(http), idempotencyKey(http), request)));
        return ResponseEntity
                .created(URI.create("/api/v1/facilities/maintenance-evidence/" + result.id()))
                .body(ApiResponse.ok(result));
    }

    // ---- internals ----------------------------------------------------------------------------

    private ApiResponse<MaintenanceResponses.WorkOrderResponse> transition(UUID workOrderId,
            MaintenanceCommands.TransitionWorkOrder.Transition transition,
            MaintenanceRequests.TransitionWorkOrder request, HttpServletRequest http) {
        return respond(service.transition(new MaintenanceCommands.TransitionWorkOrder(workOrderId,
                transition, request.notes(), request.expectedVersion(), actor(http), channel(http))));
    }

    private ApiResponse<MaintenanceResponses.WorkOrderResponse> respond(
            gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrder order) {
        return ApiResponse.ok(MaintenanceResponses.WorkOrderResponse.from(order, clock));
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
