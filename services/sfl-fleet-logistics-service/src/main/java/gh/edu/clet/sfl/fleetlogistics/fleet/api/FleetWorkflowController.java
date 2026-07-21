package gh.edu.clet.sfl.fleetlogistics.fleet.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.mapper.FleetWorkflowMapper;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.request.FleetWorkflowRequests;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.FleetWorkflowResponses.CommentResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.FleetWorkflowResponses.WorkflowHistoryResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.FleetWorkflowResponses.WorkflowItemResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.PageResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.FleetWorkflowCommands;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.FleetWorkflowRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.query.FleetWorkflowQueryService;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetWorkflowApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowPriority;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Fleet workflow queue endpoints (SRS-SFL-S166-02). */
@RestController
@RequestMapping("/api/v1/fleet/workflow-items")
class FleetWorkflowController {

    private final FleetWorkflowApplicationService workflowService;
    private final FleetWorkflowQueryService workflowQueries;
    private final FleetWorkflowMapper mapper;
    private final FleetActorResolver actorResolver;
    private final Clock clock;

    FleetWorkflowController(FleetWorkflowApplicationService workflowService,
            FleetWorkflowQueryService workflowQueries, FleetWorkflowMapper mapper,
            FleetActorResolver actorResolver, Clock clock) {
        this.workflowService = workflowService;
        this.workflowQueries = workflowQueries;
        this.mapper = mapper;
        this.actorResolver = actorResolver;
        this.clock = clock;
    }

    @PostMapping
    ResponseEntity<ApiResponse<WorkflowItemResponse>> raise(
            @Valid @RequestBody FleetWorkflowRequests.RaiseItem request, HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        FleetWorkflowItem item = workflowService.raise(new FleetWorkflowCommands.RaiseWorkflowItem(
                request.workflowType(), request.relatedRecordType(), request.relatedRecordId(),
                request.siteCode(), request.title(), request.description(), request.priority(),
                request.severity(),
                request.operatingMode() == null ? OperatingMode.ROUTINE : request.operatingMode(),
                request.assignee(), actor, actorResolver.resolveSourceChannel(httpRequest),
                actorResolver.resolveIdempotencyKey(httpRequest)));

        return ResponseEntity
                .created(URI.create("/api/v1/fleet/workflow-items/" + item.id()))
                .body(ApiResponse.ok(mapper.toResponse(item, clock.instant())));
    }

    @GetMapping
    ApiResponse<PageResponse<WorkflowItemResponse>> search(
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) FleetWorkflowStatus status,
            @RequestParam(required = false) FleetWorkflowType type,
            @RequestParam(required = false) WorkflowPriority priority,
            @RequestParam(required = false) OperatingMode operatingMode,
            @RequestParam(required = false) String assignee,
            @RequestParam(defaultValue = "false") boolean overdueOnly,
            @RequestParam(defaultValue = "false") boolean escalatedOnly,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        Instant now = clock.instant();

        FleetWorkflowRepository.WorkflowPage result = workflowQueries.search(
                new FleetWorkflowRepository.WorkflowSearchCriteria(siteCode, status, type, priority,
                        operatingMode, assignee, overdueOnly, escalatedOnly, from, to, page, size, sort),
                actor);

        return ApiResponse.ok(new PageResponse<>(
                result.content().stream().map(item -> mapper.toResponse(item, now)).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages(),
                result.page() == 0, result.page() >= result.totalPages() - 1, result.sort()));
    }

    @GetMapping("/{itemId}")
    ApiResponse<WorkflowItemResponse> findById(@PathVariable UUID itemId, HttpServletRequest httpRequest) {
        return ApiResponse.ok(mapper.toResponse(
                workflowQueries.findById(itemId, actorResolver.resolve(httpRequest)), clock.instant()));
    }

    @PatchMapping("/{itemId}/assignment")
    ApiResponse<WorkflowItemResponse> assign(@PathVariable UUID itemId,
            @Valid @RequestBody FleetWorkflowRequests.AssignItem request, HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        return ApiResponse.ok(mapper.toResponse(workflowService.assign(
                new FleetWorkflowCommands.AssignWorkflowItem(itemId, request.assignee(), request.reason(),
                        request.expectedVersion(), actor, actorResolver.resolveSourceChannel(httpRequest))),
                clock.instant()));
    }

    @PatchMapping("/{itemId}/progress")
    ApiResponse<WorkflowItemResponse> start(@PathVariable UUID itemId,
            @RequestBody(required = false) FleetWorkflowRequests.StartItem request,
            HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        Long expectedVersion = request == null ? null : request.expectedVersion();
        return ApiResponse.ok(mapper.toResponse(workflowService.start(
                new FleetWorkflowCommands.StartWorkflowItem(itemId, expectedVersion, actor,
                        actorResolver.resolveSourceChannel(httpRequest))), clock.instant()));
    }

    @PatchMapping("/{itemId}/hold")
    ApiResponse<WorkflowItemResponse> holdOrResume(@PathVariable UUID itemId,
            @Valid @RequestBody FleetWorkflowRequests.HoldItem request, HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        return ApiResponse.ok(mapper.toResponse(workflowService.holdOrResume(
                new FleetWorkflowCommands.HoldWorkflowItem(itemId, request.resume(), request.reason(),
                        request.expectedVersion(), actor, actorResolver.resolveSourceChannel(httpRequest))),
                clock.instant()));
    }

    @PatchMapping("/{itemId}/escalation")
    ApiResponse<WorkflowItemResponse> escalate(@PathVariable UUID itemId,
            @Valid @RequestBody FleetWorkflowRequests.EscalateItem request, HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        return ApiResponse.ok(mapper.toResponse(workflowService.escalate(
                new FleetWorkflowCommands.EscalateWorkflowItem(itemId, request.reason(),
                        request.expectedVersion(), actor, actorResolver.resolveSourceChannel(httpRequest))),
                clock.instant()));
    }

    @PatchMapping("/{itemId}/cancel")
    ApiResponse<WorkflowItemResponse> cancel(@PathVariable UUID itemId,
            @Valid @RequestBody FleetWorkflowRequests.CancelItem request, HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        return ApiResponse.ok(mapper.toResponse(workflowService.cancel(
                new FleetWorkflowCommands.CancelWorkflowItem(itemId, request.reason(),
                        request.expectedVersion(), actor, actorResolver.resolveSourceChannel(httpRequest))),
                clock.instant()));
    }

    @PatchMapping("/{itemId}/closure")
    ApiResponse<WorkflowItemResponse> close(@PathVariable UUID itemId,
            @Valid @RequestBody FleetWorkflowRequests.CloseItem request, HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        return ApiResponse.ok(mapper.toResponse(workflowService.close(
                new FleetWorkflowCommands.CloseWorkflowItem(itemId, request.closureReason(),
                        request.closureEvidenceId(), request.expectedVersion(), actor,
                        actorResolver.resolveSourceChannel(httpRequest))), clock.instant()));
    }

    @PatchMapping("/{itemId}/reopen")
    ApiResponse<WorkflowItemResponse> reopen(@PathVariable UUID itemId,
            @Valid @RequestBody FleetWorkflowRequests.ReopenItem request, HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        return ApiResponse.ok(mapper.toResponse(workflowService.reopen(
                new FleetWorkflowCommands.ReopenWorkflowItem(itemId, request.reason(),
                        request.expectedVersion(), actor, actorResolver.resolveSourceChannel(httpRequest))),
                clock.instant()));
    }

    @PostMapping("/{itemId}/comments")
    ResponseEntity<ApiResponse<CommentResponse>> comment(@PathVariable UUID itemId,
            @Valid @RequestBody FleetWorkflowRequests.AddComment request, HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        var comment = workflowService.comment(new FleetWorkflowCommands.CommentOnWorkflowItem(itemId,
                request.body(), actor, actorResolver.resolveSourceChannel(httpRequest)));

        return ResponseEntity
                .created(URI.create("/api/v1/fleet/workflow-items/" + itemId + "/comments/" + comment.id()))
                .body(ApiResponse.ok(mapper.toResponse(comment)));
    }

    /** The full immutable history: every transition and every comment (SRS-SFL-S166-02). */
    @GetMapping("/{itemId}/transitions")
    ApiResponse<WorkflowHistoryResponse> history(@PathVariable UUID itemId, HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        return ApiResponse.ok(new WorkflowHistoryResponse(itemId,
                workflowQueries.findTransitions(itemId, actor).stream().map(mapper::toResponse).toList(),
                workflowQueries.findComments(itemId, actor).stream().map(mapper::toResponse).toList()));
    }
}
