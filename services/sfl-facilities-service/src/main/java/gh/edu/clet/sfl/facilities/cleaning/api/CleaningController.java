package gh.edu.clet.sfl.facilities.cleaning.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.cleaning.application.CleaningCommands;
import gh.edu.clet.sfl.facilities.cleaning.application.CleaningFeedbackService;
import gh.edu.clet.sfl.facilities.cleaning.application.CleaningScheduleService;
import gh.edu.clet.sfl.facilities.cleaning.application.CleaningTaskService;
import gh.edu.clet.sfl.facilities.cleaning.application.ports.CleaningRepository;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningSchedule;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningTask;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskChecklistItem;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskOrigin;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskStatus;
import gh.edu.clet.sfl.facilities.shared.api.IdempotencyKey;
import gh.edu.clet.sfl.facilities.shared.api.PageResponse;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Routine schedules, checklist templates, cleaning tasks and occupant feedback - SRS-SFL-S169-01, -02.
 *
 * <p>{@code Idempotency-Key} is honoured on the two state-creating POSTs that a retried client request
 * could otherwise duplicate (raising an ad-hoc/booking task, raising a reactive request); every other
 * write is a PATCH guarded by the record's version and its own state machine.
 */
@RestController
@RequestMapping("/api/v1/facilities/cleaning")
@Tag(name = "S169 Cleaning & Janitorial", description = "Routine schedules, checklists, tasks and occupant feedback")
public class CleaningController {

    private final CleaningScheduleService schedules;
    private final CleaningTaskService tasks;
    private final CleaningFeedbackService feedback;
    private final Clock clock;

    public CleaningController(CleaningScheduleService schedules, CleaningTaskService tasks,
            CleaningFeedbackService feedback, Clock clock) {
        this.schedules = schedules;
        this.tasks = tasks;
        this.feedback = feedback;
        this.clock = clock;
    }

    // ---- schedules ------------------------------------------------------------------------------

    @PostMapping("/schedules")
    @Operation(summary = "Configure a routine cleaning schedule",
            description = "By site, space type (optionally one room) and frequency - SRS-SFL-S169-01.")
    public ResponseEntity<ApiResponse<CleaningResponses.ScheduleResponse>> createSchedule(
            @Valid @RequestBody CleaningRequests.CreateSchedule request, ActorContext actor, SourceChannel channel) {
        CleaningSchedule schedule = schedules.create(new CleaningCommands.CreateSchedule(request.siteCode(),
                request.name(), request.spaceType(), request.roomId(), request.frequency(), request.daysOfWeek(),
                request.timesOfDay(), request.durationMinutes(), actor, channel));
        return ResponseEntity.created(URI.create("/api/v1/facilities/cleaning/schedules/" + schedule.id()))
                .body(ApiResponse.ok(CleaningResponses.ScheduleResponse.from(schedule)));
    }

    @PatchMapping("/schedules/{scheduleId}")
    @Operation(summary = "Update a routine schedule's rota")
    public ApiResponse<CleaningResponses.ScheduleResponse> updateSchedule(@PathVariable UUID scheduleId,
            @Valid @RequestBody CleaningRequests.UpdateSchedule request, ActorContext actor, SourceChannel channel) {
        CleaningSchedule updated = schedules.update(new CleaningCommands.UpdateSchedule(scheduleId, request.name(),
                request.frequency(), request.daysOfWeek(), request.timesOfDay(), request.durationMinutes(),
                request.active(), request.expectedVersion(), actor, channel));
        return ApiResponse.ok(CleaningResponses.ScheduleResponse.from(updated));
    }

    @GetMapping("/schedules")
    @Operation(summary = "Routine schedules at a site")
    public ApiResponse<List<CleaningResponses.ScheduleResponse>> schedules(@RequestParam String siteCode,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(schedules.schedules(siteCode, actor, channel).stream()
                .map(CleaningResponses.ScheduleResponse::from).toList());
    }

    @PostMapping("/schedules/generate")
    @Operation(summary = "Run the generation sweep on demand",
            description = "Idempotent - a schedule occurrence already materialised is left alone. The scheduler "
                    + "runs this automatically; this is for an operator who does not want to wait for the timer.")
    public ApiResponse<CleaningScheduleService.GenerationResult> generate(
            @RequestParam(required = false) String siteCode, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(schedules.generate(siteCode, actor, channel));
    }

    // ---- checklist templates ---------------------------------------------------------------------

    @PostMapping("/checklist-templates")
    @Operation(summary = "Create a checklist template for a space type",
            description = "A new version supersedes the site's active template for that space type - SRS-SFL-S169-02.")
    public ResponseEntity<ApiResponse<CleaningResponses.TemplateResponse>> createTemplate(
            @Valid @RequestBody CleaningRequests.CreateTemplate request, ActorContext actor, SourceChannel channel) {
        var items = request.items().stream()
                .map(item -> new CleaningCommands.ChecklistItemSpec(item.itemCode(), item.label(), item.photoRequired()))
                .toList();
        var template = schedules.createTemplate(new CleaningCommands.CreateTemplate(request.siteCode(),
                request.spaceType(), request.name(), items, actor, channel));
        return ResponseEntity.created(URI.create("/api/v1/facilities/cleaning/checklist-templates/" + template.id()))
                .body(ApiResponse.ok(CleaningResponses.TemplateResponse.from(template)));
    }

    @GetMapping("/checklist-templates")
    @Operation(summary = "Checklist templates at a site, every version")
    public ApiResponse<List<CleaningResponses.TemplateResponse>> templates(@RequestParam String siteCode,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(schedules.templates(siteCode, actor, channel).stream()
                .map(CleaningResponses.TemplateResponse::from).toList());
    }

    // ---- tasks ------------------------------------------------------------------------------------

    @PostMapping("/tasks")
    @Operation(summary = "Raise an ad-hoc or booking setup/teardown task",
            description = "A booking-origin task must resolve in S159 or is refused (CLEANING_BOOKING_UNLINKED) - "
                    + "SRS-SFL-S169-01. Routine and event tasks are raised by the sweep and by S173, not here.")
    public ResponseEntity<ApiResponse<CleaningResponses.TaskResponse>> raiseTask(
            @Valid @RequestBody CleaningRequests.RaiseTask request, ActorContext actor, SourceChannel channel,
            @IdempotencyKey String idempotencyKey) {
        CleaningTask task = tasks.raise(new CleaningCommands.RaiseTask(request.roomId(), request.origin(),
                request.title(), request.description(), request.bookingId(), request.windowStart(), request.dueBy(),
                actor, channel, idempotencyKey, request));
        return ResponseEntity.created(URI.create("/api/v1/facilities/cleaning/tasks/" + task.id()))
                .body(ApiResponse.ok(CleaningResponses.TaskResponse.from(task, clock.instant())));
    }

    @PostMapping("/requests")
    @Operation(summary = "Raise a reactive cleaning request",
            description = "Any occupant holding FACILITIES_CLEANING_REQUEST - SRS-SFL-S169-01. An IFIMP_REQUESTER "
                    + "sees only the requests they raised.")
    public ResponseEntity<ApiResponse<CleaningResponses.TaskResponse>> raiseRequest(
            @Valid @RequestBody CleaningRequests.RaiseRequest request, ActorContext actor, SourceChannel channel,
            @IdempotencyKey String idempotencyKey) {
        CleaningTask task = tasks.request(new CleaningCommands.RaiseRequest(request.roomId(), request.description(),
                request.bookingId(), actor, channel, idempotencyKey, request));
        return ResponseEntity.created(URI.create("/api/v1/facilities/cleaning/tasks/" + task.id()))
                .body(ApiResponse.ok(CleaningResponses.TaskResponse.from(task, clock.instant())));
    }

    @PatchMapping("/tasks/{taskId}/assignment")
    @Operation(summary = "Assign a task to in-house staff or a registered vendor")
    public ApiResponse<CleaningResponses.TaskResponse> assign(@PathVariable UUID taskId,
            @Valid @RequestBody CleaningRequests.AssignTask request, ActorContext actor, SourceChannel channel) {
        CleaningTask task = tasks.assign(new CleaningCommands.AssignTask(taskId, request.assigneeType(),
                request.assignedTo(), request.vendorId(), request.expectedVersion(), actor, channel));
        return ApiResponse.ok(CleaningResponses.TaskResponse.from(task, clock.instant()));
    }

    @PatchMapping("/tasks/{taskId}/start")
    @Operation(summary = "The assignee has arrived and started the clean")
    public ApiResponse<CleaningResponses.TaskResponse> start(@PathVariable UUID taskId,
            @Valid @RequestBody CleaningRequests.StartTask request, ActorContext actor, SourceChannel channel) {
        CleaningTask task = tasks.start(new CleaningCommands.StartTask(taskId, request.expectedVersion(), actor,
                channel));
        return ApiResponse.ok(CleaningResponses.TaskResponse.from(task, clock.instant()));
    }

    @PatchMapping("/tasks/{taskId}/checklist/{itemId}")
    @Operation(summary = "Mark one checklist item done, with photo evidence by reference and hash",
            description = "Never the image itself - a reference to where it is stored, plus the SHA-256 of its "
                    + "bytes (SRS-SFL-S169-02).")
    public ApiResponse<CleaningResponses.ChecklistItemResponse> recordItem(@PathVariable UUID taskId,
            @PathVariable UUID itemId, @Valid @RequestBody CleaningRequests.RecordChecklistItem request,
            ActorContext actor, SourceChannel channel) {
        TaskChecklistItem item = tasks.recordItem(new CleaningCommands.RecordChecklistItem(taskId, itemId,
                request.done(), request.photoReference(), request.photoContentHash(), request.notes(), actor,
                channel));
        return ApiResponse.ok(CleaningResponses.ChecklistItemResponse.from(item));
    }

    @PatchMapping("/tasks/{taskId}/completion")
    @Operation(summary = "Complete the task",
            description = "Refused with CLEANING_CHECKLIST_INCOMPLETE, naming every blocking item, while a "
                    + "required checklist item is unaddressed or a required photo is missing (SRS-SFL-S169-02).")
    public ApiResponse<CleaningResponses.TaskResponse> complete(@PathVariable UUID taskId,
            @Valid @RequestBody CleaningRequests.CompleteTask request, ActorContext actor, SourceChannel channel) {
        CleaningTask task = tasks.complete(new CleaningCommands.CompleteTask(taskId, request.notes(),
                request.vendorReportedCompletedAt(), request.expectedVersion(), actor, channel));
        return ApiResponse.ok(CleaningResponses.TaskResponse.from(task, clock.instant()));
    }

    @PatchMapping("/tasks/{taskId}/cancellation")
    @Operation(summary = "Cancel a task, with a reason")
    public ApiResponse<CleaningResponses.TaskResponse> cancel(@PathVariable UUID taskId,
            @Valid @RequestBody CleaningRequests.CancelTask request, ActorContext actor, SourceChannel channel) {
        CleaningTask task = tasks.cancel(new CleaningCommands.CancelTask(taskId, request.reason(),
                request.expectedVersion(), actor, channel));
        return ApiResponse.ok(CleaningResponses.TaskResponse.from(task, clock.instant()));
    }

    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "Read one cleaning task")
    public ApiResponse<CleaningResponses.TaskResponse> findTask(@PathVariable UUID taskId, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(CleaningResponses.TaskResponse.from(tasks.find(taskId, actor, channel), clock.instant()));
    }

    @GetMapping("/tasks/{taskId}/checklist")
    @Operation(summary = "The task's checklist")
    public ApiResponse<List<CleaningResponses.ChecklistItemResponse>> checklist(@PathVariable UUID taskId,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(tasks.checklist(taskId, actor, channel).stream()
                .map(CleaningResponses.ChecklistItemResponse::from).toList());
    }

    @GetMapping("/tasks")
    @Operation(summary = "Search cleaning tasks",
            description = "A vendor technician sees only tasks assigned to them; an occupant sees only requests "
                    + "they raised - whatever the filters say.")
    public ApiResponse<PageResponse<CleaningResponses.TaskResponse>> search(
            @RequestParam(required = false) String siteCode, @RequestParam(required = false) UUID roomId,
            @RequestParam(required = false) TaskStatus status, @RequestParam(required = false) TaskOrigin origin,
            @RequestParam(required = false) String requestedBy, @RequestParam(required = false) String assignedTo,
            @RequestParam(required = false) UUID bookingId, @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size, ActorContext actor, SourceChannel channel) {
        Instant now = clock.instant();
        return ApiResponse.ok(PageResponse.from(
                tasks.search(new CleaningRepository.TaskQuery(siteCode, roomId, status, origin, requestedBy,
                        assignedTo, bookingId, from, to, page, size), actor, channel),
                task -> CleaningResponses.TaskResponse.from(task, now)));
    }

    // ---- feedback and low-rating review -------------------------------------------------------

    @PostMapping("/tasks/{taskId}/feedback")
    @Operation(summary = "Submit occupant feedback on a completed clean",
            description = "Rating 1-5 plus an optional comment - SRS-SFL-S169-02. Repeated low ratings for the "
                    + "same space or vendor surface a flag for supervisor review.")
    public ResponseEntity<ApiResponse<CleaningResponses.FeedbackResponse>> submitFeedback(@PathVariable UUID taskId,
            @Valid @RequestBody CleaningRequests.SubmitFeedback request, ActorContext actor, SourceChannel channel) {
        var submitted = feedback.submit(new CleaningCommands.SubmitFeedback(taskId, request.rating(),
                request.comment(), actor, channel));
        return ResponseEntity.created(URI.create("/api/v1/facilities/cleaning/tasks/" + taskId + "/feedback/"
                + submitted.id())).body(ApiResponse.ok(CleaningResponses.FeedbackResponse.from(submitted)));
    }

    @GetMapping("/tasks/{taskId}/feedback")
    @Operation(summary = "Feedback given on one task")
    public ApiResponse<List<CleaningResponses.FeedbackResponse>> feedbackForTask(@PathVariable UUID taskId,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(feedback.forTask(taskId, actor, channel).stream()
                .map(CleaningResponses.FeedbackResponse::from).toList());
    }

    @GetMapping("/low-rating-flags")
    @Operation(summary = "The supervisor low-rating review queue")
    public ApiResponse<List<CleaningResponses.FlagResponse>> flags(@RequestParam String siteCode,
            @RequestParam(required = false) Boolean open, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(feedback.flags(siteCode, open, actor, channel).stream()
                .map(CleaningResponses.FlagResponse::from).toList());
    }

    @PatchMapping("/low-rating-flags/{flagId}/review")
    @Operation(summary = "Record a supervisor's review of a low-rating flag")
    public ApiResponse<CleaningResponses.FlagResponse> review(@PathVariable UUID flagId,
            @Valid @RequestBody CleaningRequests.ReviewFlag request, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(CleaningResponses.FlagResponse.from(
                feedback.review(new CleaningCommands.ReviewFlag(flagId, request.notes(), actor, channel))));
    }
}
