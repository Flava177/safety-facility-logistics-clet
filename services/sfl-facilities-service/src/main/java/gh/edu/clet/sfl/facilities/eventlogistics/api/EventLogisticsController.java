package gh.edu.clet.sfl.facilities.eventlogistics.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.eventlogistics.application.EventHandoffService;
import gh.edu.clet.sfl.facilities.eventlogistics.application.EventLogisticsCommands;
import gh.edu.clet.sfl.facilities.eventlogistics.application.EventReadinessService;
import gh.edu.clet.sfl.facilities.eventlogistics.application.EventResourceRequestService;
import gh.edu.clet.sfl.facilities.eventlogistics.application.EventRiskCriteriaService;
import gh.edu.clet.sfl.facilities.eventlogistics.application.EventSetupTaskService;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventResourceRequest;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventSetupTask;
import gh.edu.clet.sfl.facilities.shared.application.vendor.SignedVendorMessage;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * Event set-up and resource requests - SRS-SFL-S173-01..04.
 *
 * <h2>The hand-off endpoint</h2>
 *
 * <p>{@code POST /handoffs} is vendor-inbound (NFR-SEC2), not an ordinary write: it is signed by CCP
 * Events (S078) over the raw request body and verified by {@link gh.edu.clet.sfl.facilities.shared
 * .application.vendor.VendorMessageVerifier}. This controller reads the body as a raw string - the
 * signature is over those exact bytes, never a re-serialisation - and parses it only after building the
 * {@link SignedVendorMessage}, so a parse failure of a forged body cannot short-circuit the signature
 * check. {@code X-SFL-Source}, {@code X-SFL-Signature} and {@code X-SFL-Signed-At} carry the envelope;
 * the actor still comes from the caller's own identity (JWT or dev headers), because
 * {@code FACILITIES_EVENT_HANDOFF_INGEST} is a permission an integration principal holds, distinct from
 * the vendor signature that authenticates the payload's origin.
 */
@RestController
@RequestMapping("/api/v1/facilities/event-logistics")
@Tag(name = "S173 Event logistics", description = "Hand-off intake, resource requests, risk-gated confirmation, "
        + "escalation and post-event reconciliation")
public class EventLogisticsController {

    private final EventHandoffService handoffs;
    private final EventResourceRequestService requests;
    private final EventSetupTaskService tasks;
    private final EventReadinessService readiness;
    private final EventRiskCriteriaService riskCriteria;
    private final ObjectMapper mapper;

    public EventLogisticsController(EventHandoffService handoffs, EventResourceRequestService requests,
            EventSetupTaskService tasks, EventReadinessService readiness, EventRiskCriteriaService riskCriteria,
            ObjectMapper mapper) {
        this.handoffs = handoffs;
        this.requests = requests;
        this.tasks = tasks;
        this.readiness = readiness;
        this.riskCriteria = riskCriteria;
        this.mapper = mapper;
    }

    // ---- S173-01: hand-off intake ---------------------------------------------------------------

    @PostMapping("/handoffs")
    @Operation(summary = "Accept a CCP Events (S078) hand-off",
            description = "SRS-SFL-S173-01. A confirmed event creates a set-up task; an unresolvable "
                    + "reference is rejected. Signed over the raw body by CCP-EVENTS-SIM in development.")
    @SuppressWarnings("unchecked")
    public ResponseEntity<ApiResponse<EventLogisticsResponses.HandoffResponse>> acceptHandoff(
            @RequestBody String rawBody, @RequestHeader("X-SFL-Source") String source,
            @RequestHeader("X-SFL-Signature") String signature, @RequestHeader("X-SFL-Signed-At") Instant signedAt,
            ActorContext actor) {
        Map<String, Object> envelope;
        try {
            envelope = mapper.readValue(rawBody, Map.class);
        } catch (Exception malformed) {
            // Not yet authenticated: refused as a validation failure rather than passed to the verifier,
            // which needs a parsed payload map to check required fields against.
            throw new FacilitiesException.ValidationFailedException("The hand-off body is not valid JSON.");
        }
        String messageType = text(envelope, "messageType");
        String idempotencyKey = text(envelope, "idempotencyKey");
        String siteCode = text(envelope, "siteCode");
        SignedVendorMessage message = new SignedVendorMessage(source, messageType, idempotencyKey, siteCode,
                signedAt, signature, rawBody, envelope);
        EventHandoffService.HandoffResult result = handoffs.accept(message, actor);
        EventLogisticsResponses.HandoffResponse body = result.handoff() == null
                ? new EventLogisticsResponses.HandoffResponse(null, null, "DUPLICATE", null, null, true)
                : EventLogisticsResponses.HandoffResponse.from(result.handoff(), result.duplicate());
        return ResponseEntity.status(result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ApiResponse.ok(body));
    }

    private static String text(Map<String, Object> envelope, String field) {
        Object value = envelope.get(field);
        return value == null ? null : String.valueOf(value);
    }

    // ---- set-up tasks -----------------------------------------------------------------------

    @GetMapping("/setup-tasks/{id}")
    @Operation(summary = "One set-up task")
    public ApiResponse<EventLogisticsResponses.SetupTaskResponse> findTask(@PathVariable UUID id, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(EventLogisticsResponses.SetupTaskResponse.from(tasks.findById(id, actor, channel)));
    }

    @GetMapping("/setup-tasks/{id}/readiness")
    @Operation(summary = "One event's consolidated readiness view",
            description = "SRS-SFL-S173-02: every resource request's status rolled up, with conflicts, manual "
                    + "coordination items and escalations named.")
    public ApiResponse<EventLogisticsResponses.ReadinessResponse> readiness(@PathVariable UUID id, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(EventLogisticsResponses.ReadinessResponse.from(tasks.readiness(id, actor, channel)));
    }

    @GetMapping("/setup-tasks")
    @Operation(summary = "Upcoming events by readiness status",
            description = "The S173 dashboard: upcoming events, unresourced tasks, hand-off completeness and "
                    + "cross-unit resource conflicts, worst readiness first.")
    public ApiResponse<List<EventLogisticsResponses.ReadinessResponse>> upcoming(
            @RequestParam(required = false) String siteCode, @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(tasks.upcoming(siteCode, from, to, actor, channel).stream()
                .map(EventLogisticsResponses.ReadinessResponse::from).toList());
    }

    @GetMapping("/setup-tasks/{id}/handoffs")
    @Operation(summary = "The hand-off register for one event", description = "Accepted and rejected, in order.")
    public ApiResponse<List<EventLogisticsResponses.HandoffResponse>> handoffHistory(@PathVariable UUID id,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(tasks.handoffs(id, actor, channel).stream()
                .map(handoff -> EventLogisticsResponses.HandoffResponse.from(handoff, false)).toList());
    }

    // ---- S173-01/02: decomposition and routing ---------------------------------------------------

    @PostMapping("/setup-tasks/{id}/resource-requests")
    @Operation(summary = "Decompose a set-up task into typed resource requests and route them",
            description = "SRS-SFL-S173-01: staging, AV, security, catering, signage as structured requests, "
                    + "never free text. Each is routed to its owning system immediately.")
    public ResponseEntity<ApiResponse<List<EventLogisticsResponses.ResourceRequestResponse>>> decompose(
            @PathVariable UUID id, @Valid @RequestBody EventLogisticsRequests.Decompose request, ActorContext actor,
            SourceChannel channel) {
        List<EventResourceRequest> created = requests.decompose(new EventLogisticsCommands.DecomposeSetupTask(id,
                request.requests().stream()
                        .map(line -> new EventLogisticsCommands.NewResourceRequest(line.resourceType(),
                                line.description(), line.quantity(), line.bookableResourceId(), line.neededFrom(),
                                line.neededTo()))
                        .toList(),
                request.applyTemplate(), actor, channel));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created.stream().map(EventLogisticsResponses.ResourceRequestResponse::from)
                        .toList()));
    }

    @PatchMapping("/resource-requests/{id}/route")
    @Operation(summary = "Route a request to its owning system again",
            description = "After a conflict was resolved elsewhere, or once the venue this line depends on is "
                    + "booked.")
    public ApiResponse<EventLogisticsResponses.ResourceRequestResponse> route(@PathVariable UUID id,
            ActorContext actor, SourceChannel channel) {
        EventResourceRequest routed = requests.reroute(
                new EventLogisticsCommands.RouteResourceRequest(id, actor, channel));
        return ApiResponse.ok(EventLogisticsResponses.ResourceRequestResponse.from(routed));
    }

    @PatchMapping("/resource-requests/{id}/manual-coordination/accept")
    @Operation(summary = "Accept a manual coordination item",
            description = "SRS-SFL-S173-04: a named person takes responsibility for a request whose owning "
                    + "system is not built or not available (e.g. S172 catering).")
    public ApiResponse<EventLogisticsResponses.ResourceRequestResponse> acceptManual(@PathVariable UUID id,
            @Valid @RequestBody EventLogisticsRequests.AcceptManualCoordination request, ActorContext actor,
            SourceChannel channel) {
        EventResourceRequest accepted = requests.acceptManualCoordination(
                new EventLogisticsCommands.AcceptManualCoordination(id, request.arrangedWith(), actor, channel));
        return ApiResponse.ok(EventLogisticsResponses.ResourceRequestResponse.from(accepted));
    }

    @PatchMapping("/resource-requests/{id}/cancel")
    @Operation(summary = "Cancel a resource request no longer needed")
    public ApiResponse<EventLogisticsResponses.ResourceRequestResponse> cancelRequest(@PathVariable UUID id,
            @Valid @RequestBody EventLogisticsRequests.CancelResourceRequest request, ActorContext actor,
            SourceChannel channel) {
        EventResourceRequest cancelled = requests.cancel(
                new EventLogisticsCommands.CancelResourceRequest(id, request.reason(), actor, channel));
        return ApiResponse.ok(EventLogisticsResponses.ResourceRequestResponse.from(cancelled));
    }

    // ---- S173-03: risk assessment and confirmation ------------------------------------------------

    @PutMapping("/setup-tasks/{id}/risk-assessment")
    @Operation(summary = "Link an S165 risk assessment to a higher-risk event",
            description = "SRS-SFL-S173-03. Refused unless S173's projection holds a current version.")
    public ApiResponse<EventLogisticsResponses.SetupTaskResponse> linkRiskAssessment(@PathVariable UUID id,
            @Valid @RequestBody EventLogisticsRequests.LinkRiskAssessment request, ActorContext actor,
            SourceChannel channel) {
        EventSetupTask linked = tasks.linkRiskAssessment(new EventLogisticsCommands.LinkRiskAssessment(id,
                request.assessmentId(), request.version(), actor, channel));
        return ApiResponse.ok(EventLogisticsResponses.SetupTaskResponse.from(linked));
    }

    @PatchMapping("/setup-tasks/{id}/confirm")
    @Operation(summary = "Confirm a set-up task",
            description = "SRS-SFL-S173-03: a higher-risk event is refused without a linked, current S165 "
                    + "assessment. A routine event is exempt.")
    public ApiResponse<EventLogisticsResponses.SetupTaskResponse> confirm(@PathVariable UUID id, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(EventLogisticsResponses.SetupTaskResponse.from(
                tasks.confirm(new EventLogisticsCommands.ConfirmSetupTask(id, actor, channel))));
    }

    @PatchMapping("/setup-tasks/{id}/complete")
    @Operation(summary = "Mark a set-up task complete",
            description = "SRS-SFL-S173-04: refused while a requested-status resource request is unresolved "
                    + "and has not been escalated.")
    public ApiResponse<EventLogisticsResponses.SetupTaskResponse> complete(@PathVariable UUID id,
            @Valid @RequestBody(required = false) EventLogisticsRequests.CompleteSetupTask request,
            ActorContext actor, SourceChannel channel) {
        String notes = request == null ? null : request.notes();
        return ApiResponse.ok(EventLogisticsResponses.SetupTaskResponse.from(
                tasks.complete(new EventLogisticsCommands.CompleteSetupTask(id, notes, actor, channel))));
    }

    // ---- S173-04: reconciliation and templates -----------------------------------------------------

    @PostMapping("/setup-tasks/{id}/reconciliation")
    @Operation(summary = "Record post-event reconciliation",
            description = "SRS-SFL-S173-04: what was actually delivered against what was requested, per line. "
                    + "Gaps feed the event category's template.")
    public ResponseEntity<ApiResponse<List<EventLogisticsResponses.ReconciliationLineResponse>>> reconcile(
            @PathVariable UUID id, @Valid @RequestBody EventLogisticsRequests.Reconcile request, ActorContext actor,
            SourceChannel channel) {
        List<gh.edu.clet.sfl.facilities.eventlogistics.domain.EventReconciliationLine> recorded = readiness.reconcile(
                new EventLogisticsCommands.RecordReconciliation(id, request.lines().stream()
                        .map(line -> new EventLogisticsCommands.ReconciliationEntry(line.resourceRequestId(),
                                line.outcome(), line.deliveredQuantity(), line.notes()))
                        .toList(), actor, channel));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(recorded.stream().map(EventLogisticsResponses.ReconciliationLineResponse::from)
                        .toList()));
    }

    @GetMapping("/setup-tasks/{id}/reconciliation")
    @Operation(summary = "Reconciliation lines recorded for one event")
    public ApiResponse<List<EventLogisticsResponses.ReconciliationLineResponse>> reconciliationFor(
            @PathVariable UUID id, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(readiness.reconciliation(id, actor, channel).stream()
                .map(EventLogisticsResponses.ReconciliationLineResponse::from).toList());
    }

    @GetMapping("/templates")
    @Operation(summary = "Event category templates",
            description = "The lessons a persistent reconciliation gap has fed back (S173-04).")
    public ApiResponse<List<EventLogisticsResponses.TemplateLineResponse>> templates(
            @RequestParam String siteCode, @RequestParam(required = false) String eventCategory, ActorContext actor,
            SourceChannel channel) {
        int threshold = readiness.templateGapThreshold(siteCode);
        return ApiResponse.ok(readiness.templates(siteCode, eventCategory, actor, channel).stream()
                .map(line -> EventLogisticsResponses.TemplateLineResponse.from(line, threshold)).toList());
    }

    // ---- S173-03: risk criteria configuration, and the integration position -----------------------

    @GetMapping("/risk-criteria")
    @Operation(summary = "The higher-risk criteria in force")
    public ApiResponse<EventLogisticsResponses.RiskCriteriaResponse> riskCriteria(
            @RequestParam(required = false) String siteCode, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(EventLogisticsResponses.RiskCriteriaResponse.from(
                riskCriteria.criteria(siteCode, actor, channel)));
    }

    @PutMapping("/risk-criteria")
    @Operation(summary = "Configure the higher-risk criteria",
            description = "SRS-SFL-S173-03: attendance threshold, external contractors, temporary structures "
                    + "and the higher-risk category list. Requires FACILITIES_EVENT_RISK_CATEGORY_MANAGE.")
    public ApiResponse<EventLogisticsResponses.RiskCriteriaResponse> configureRiskCriteria(
            @RequestParam(required = false) String siteCode,
            @RequestBody EventLogisticsRequests.ConfigureRiskCriteria request, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(EventLogisticsResponses.RiskCriteriaResponse.from(riskCriteria.configure(
                new EventLogisticsCommands.ConfigureRiskCriteria(siteCode, request.attendanceThreshold(),
                        request.externalContractorsAreHigherRisk(), request.temporaryStructuresAreHigherRisk(),
                        request.higherRiskCategories(), actor, channel))));
    }

    @GetMapping("/integration")
    @Operation(summary = "S173's dependency position",
            description = "S078 (external, no query API), owning-system availability, and S165's unbuilt "
                    + "projection - never reported as more integrated than it is.")
    public ApiResponse<EventLogisticsResponses.IntegrationResponse> integration(
            @RequestParam(required = false) String siteCode, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(EventLogisticsResponses.IntegrationResponse.from(
                riskCriteria.integration(siteCode, actor, channel)));
    }
}
