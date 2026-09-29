package gh.edu.clet.sfl.facilities.cleaning.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.cleaning.application.ports.BookingDirectoryPort;
import gh.edu.clet.sfl.facilities.cleaning.application.ports.CleaningRepository;
import gh.edu.clet.sfl.facilities.cleaning.domain.AssigneeType;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningTask;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningVendor;
import gh.edu.clet.sfl.facilities.cleaning.domain.PhotoEvidence;
import gh.edu.clet.sfl.facilities.cleaning.domain.SlaBreach;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskChecklistItem;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskOrigin;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskStatus;
import gh.edu.clet.sfl.facilities.cleaning.domain.policy.ChecklistCompletionPolicy;
import gh.edu.clet.sfl.facilities.cleaning.domain.policy.SlaCompliancePolicy;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.shared.application.port.IdempotencyPort;
import gh.edu.clet.sfl.facilities.shared.application.port.RepositoryPage;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The cleaning task workflow - SRS-SFL-S169-01 and -02: raise, assign, start, work the checklist,
 * complete with evidence, cancel.
 *
 * <h2>A task claiming a booking must be able to prove it</h2>
 *
 * SRS-SFL-S169-01's error state: "a task claiming to originate from a booking with no resolvable S159
 * reference; rejected at creation." Every path here that accepts a {@code bookingId} - a
 * supervisor-raised booking task or an occupant's request mentioning one - resolves it through
 * {@link BookingDirectoryPort} before anything is written. Unresolvable is refused with
 * {@code CLEANING_BOOKING_UNLINKED} and audited as {@code CLEANING_BOOKING_TASK_REJECTED} in a
 * transaction of its own, so the refusal is on the chain even though the command rolled back.
 *
 * <h2>The completion gate</h2>
 *
 * {@link ChecklistCompletionPolicy} runs before the transition, and its refusal names every blocking
 * item. The completion time is the service clock; a vendor's own claim is stored beside it and a
 * disagreement becomes a recorded discrepancy, never a correction.
 */
@Service
public class CleaningTaskService {

    private final CleaningSupport support;
    private final CleaningRepository repository;
    private final BookingDirectoryPort bookings;
    private final SlaEvaluator sla;
    private final IdempotencyPort idempotency;

    public CleaningTaskService(CleaningSupport support, BookingDirectoryPort bookings, SlaEvaluator sla,
            IdempotencyPort idempotency) {
        this.support = support;
        this.repository = support.repository();
        this.bookings = bookings;
        this.sla = sla;
        this.idempotency = idempotency;
    }

    // =============================================================================================
    // Raising
    // =============================================================================================

    /** A supervisor-raised task: ADHOC, or a booking setup/teardown clean the automatic path did not raise. */
    @Transactional
    public CleaningTask raise(CleaningCommands.RaiseTask command) {
        ActorContext actor = command.actor();
        TaskOrigin origin = command.origin() == null ? TaskOrigin.ADHOC : command.origin();
        if (origin != TaskOrigin.ADHOC && !origin.claimsBooking()) {
            throw new FacilitiesException.ValidationFailedException(
                    "Only ad-hoc and booking setup/teardown tasks are raised here. Routine tasks come from a "
                            + "schedule, reactive ones from a request and event ones from S173.");
        }
        Optional<CleaningTask> replayed = replay("raise-cleaning-task", command.idempotencyKey(),
                command.idempotencyPayload());
        if (replayed.isPresent()) {
            return replayed.get();
        }

        if (origin.claimsBooking()) {
            // Resolved before the room or the permission is used for anything else, so an unresolvable
            // claim is refused (and audited) whoever sent it and whatever room it named.
            BookingDirectoryPort.BookingSnapshot booking = resolveBookingOrReject(command.bookingId(),
                    command.roomId(), origin, actor, command.channel());
            FacilityRoom room = support.requireRoom(booking.roomId());
            support.authorization().require(actor, SflPermission.FACILITIES_CLEANING_TASK_SUPERVISE, room.siteCode(),
                    command.channel(), "CleaningTask", "new");
            if (booking.roomId() != null && command.roomId() != null && !booking.roomId().equals(command.roomId())) {
                throw new FacilitiesException.ValidationFailedException(
                        "Booking " + booking.bookingReference() + " is for " + booking.roomCode()
                                + ", not the room this task names.");
            }
            repository.findTasksForBooking(booking.bookingId()).stream()
                    .filter(task -> task.origin() == origin && task.isLive())
                    .findFirst()
                    .ifPresent(existing -> {
                        throw new FacilitiesException.ValidationFailedException(
                                "Booking " + booking.bookingReference() + " already has its " + origin
                                        + " task " + existing.taskNumber() + ".");
                    });
            BookingTaskWindows.Window window = BookingTaskWindows.of(origin, booking,
                    support.configuration().bookingTaskLength(room.siteCode()));
            CleaningTask task = support.raise(room, origin, command.title() == null
                    ? BookingTaskWindows.title(origin, booking) : command.title(), command.description(), null,
                    null, booking.bookingId(), booking.bookingReference(), null, null, window.start(),
                    window.dueBy(), actor.actorId(), actor, command.channel());
            remember("raise-cleaning-task", command.idempotencyKey(), command.idempotencyPayload(), task, actor);
            return task;
        }

        FacilityRoom room = support.requireRoom(command.roomId());
        support.authorization().require(actor, SflPermission.FACILITIES_CLEANING_TASK_SUPERVISE, room.siteCode(),
                command.channel(), "CleaningTask", "new");
        if (command.windowStart() == null || command.dueBy() == null) {
            throw new FacilitiesException.ValidationFailedException(
                    "An ad-hoc cleaning task needs a window start and a due time.");
        }
        CleaningTask task = support.raise(room, TaskOrigin.ADHOC,
                command.title() == null ? "Ad-hoc clean of " + room.roomCode() : command.title(),
                command.description(), null, null, null, null, null, null, command.windowStart(), command.dueBy(),
                actor.actorId(), actor, command.channel());
        remember("raise-cleaning-task", command.idempotencyKey(), command.idempotencyPayload(), task, actor);
        return task;
    }

    /**
     * A reactive request, raised directly by an occupant - SRS-SFL-S169-01 "reactive (unscheduled)
     * cleaning requests can be raised directly by any occupant". Due after the configured target.
     */
    @Transactional
    public CleaningTask request(CleaningCommands.RaiseRequest command) {
        ActorContext actor = command.actor();
        Optional<CleaningTask> replayed = replay("raise-cleaning-request", command.idempotencyKey(),
                command.idempotencyPayload());
        if (replayed.isPresent()) {
            return replayed.get();
        }
        BookingDirectoryPort.BookingSnapshot booking = command.bookingId() == null ? null
                : resolveBookingOrReject(command.bookingId(), command.roomId(), TaskOrigin.REACTIVE, actor,
                        command.channel());
        FacilityRoom room = support.requireRoom(command.roomId() != null ? command.roomId()
                : booking == null ? null : booking.roomId());
        support.authorization().require(actor, SflPermission.FACILITIES_CLEANING_REQUEST, room.siteCode(),
                command.channel(), "CleaningTask", "new");
        if (command.description() == null || command.description().isBlank()) {
            throw new FacilitiesException.ValidationFailedException("Say what needs cleaning.");
        }
        Instant at = support.now();
        CleaningTask task = support.raise(room, TaskOrigin.REACTIVE, "Cleaning request for " + room.roomCode(),
                command.description(), null, null, booking == null ? null : booking.bookingId(),
                booking == null ? null : booking.bookingReference(), null, null, at,
                at.plus(support.configuration().reactiveTarget(room.siteCode())), actor.actorId(), actor,
                command.channel());
        remember("raise-cleaning-request", command.idempotencyKey(), command.idempotencyPayload(), task, actor);
        return task;
    }

    /**
     * Resolves a claimed booking in S159, refusing an unresolvable one.
     *
     * <p>"Unresolvable" covers: no such booking, one at a site the caller cannot see (from here that is
     * indistinguishable from absent, and must be), and one that no longer holds its space - a cancelled
     * booking has no event to clean up after, so a task claiming it is claiming nothing.
     */
    private BookingDirectoryPort.BookingSnapshot resolveBookingOrReject(UUID bookingId, UUID roomId,
            TaskOrigin origin, ActorContext actor, SourceChannel channel) {
        Optional<BookingDirectoryPort.BookingSnapshot> found = bookingId == null ? Optional.empty()
                : bookings.resolve(bookingId);
        String reason = null;
        if (found.isEmpty()) {
            reason = bookingId == null ? "no booking reference given" : "S159 has no booking " + bookingId;
        } else if (!support.authorization().canAccessSite(actor, found.get().siteCode())) {
            reason = "S159 has no booking " + bookingId + " within the caller's site scope";
        } else if (!found.get().live()) {
            reason = "booking " + found.get().bookingReference() + " no longer holds its space";
        }
        if (reason == null) {
            return found.get();
        }
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("claimedBookingId", bookingId == null ? null : bookingId.toString());
        facts.put("origin", origin.name());
        facts.put("roomId", roomId == null ? null : roomId.toString());
        facts.put("reason", reason);
        String site = found.map(BookingDirectoryPort.BookingSnapshot::siteCode)
                .filter(code -> support.authorization().canAccessSite(actor, code))
                .orElseGet(() -> roomId == null ? "*" : support.facilities().findRoom(roomId)
                        .map(FacilityRoom::siteCode).orElse("*"));
        support.refusals().record(actor, channel, AuditAction.CLEANING_BOOKING_TASK_REJECTED, "CleaningTask",
                "rejected", site, facts);
        throw new FacilitiesException(FacilitiesErrorCode.CLEANING_BOOKING_UNLINKED,
                FacilitiesErrorCode.CLEANING_BOOKING_UNLINKED.defaultMessage() + " (" + reason + ")");
    }

    // =============================================================================================
    // Working a task
    // =============================================================================================

    /** Assign to in-house staff or to a registered vendor's technician. Supervisors only. */
    @Transactional
    public CleaningTask assign(CleaningCommands.AssignTask command) {
        ActorContext actor = command.actor();
        CleaningTask task = support.requireTask(command.taskId());
        support.authorization().require(actor, SflPermission.FACILITIES_CLEANING_TASK_SUPERVISE, task.siteCode(),
                command.channel(), "CleaningTask", task.id().toString());
        task.metadata().requireVersion(command.expectedVersion(), "Cleaning task", task.id());
        UUID vendorId = null;
        if (command.assigneeType() == AssigneeType.VENDOR) {
            vendorId = requireAssignableVendor(command.vendorId(), task.siteCode()).id();
        }
        CleaningTask assigned = repository.saveTask(task.assign(command.assigneeType(), command.assignedTo(), vendorId,
                actor.actorId(), support.now(), command.channel(), actor.correlationId()));
        support.audit().record(actor, command.channel(), AuditAction.CLEANING_TASK_ASSIGNED, "CleaningTask",
                assigned.id().toString(), assigned.siteCode(), task, assigned);
        return assigned;
    }

    /** The assignee has arrived. For a vendor task this stops the SLA response clock. */
    @Transactional
    public CleaningTask start(CleaningCommands.StartTask command) {
        ActorContext actor = command.actor();
        CleaningTask task = support.requireTask(command.taskId());
        support.assertVisible(actor, task, command.channel());
        support.requireMayExecute(actor, task, command.channel());
        task.metadata().requireVersion(command.expectedVersion(), "Cleaning task", task.id());
        CleaningTask started = repository.saveTask(task.start(actor.actorId(), support.now(), command.channel(),
                actor.correlationId()));
        support.audit().record(actor, command.channel(), AuditAction.CLEANING_TASK_STARTED, "CleaningTask",
                started.id().toString(), started.siteCode(), task, started);
        sla.evaluateTimes(started, actor, command.channel());
        return started;
    }

    /**
     * Marks one checklist item done (or not), with photo evidence by reference and hash - never bytes.
     * Only while the task is in progress: a checklist ticked before anybody arrived is not evidence.
     */
    @Transactional
    public TaskChecklistItem recordItem(CleaningCommands.RecordChecklistItem command) {
        ActorContext actor = command.actor();
        CleaningTask task = support.requireTask(command.taskId());
        support.assertVisible(actor, task, command.channel());
        support.requireMayExecute(actor, task, command.channel());
        if (task.status() != TaskStatus.IN_PROGRESS) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Checklist items are recorded while the task is in progress; this one is " + task.status() + ".");
        }
        TaskChecklistItem item = repository.findChecklistItem(command.itemId())
                .filter(found -> found.taskId().equals(task.id()))
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Checklist item", command.itemId()));
        PhotoEvidence photo = PhotoEvidence.ofNullable(command.photoReference(), command.photoContentHash());
        TaskChecklistItem recorded = repository.saveChecklistItem(item.record(command.done(), photo, command.notes(),
                actor.actorId(), support.now(), command.channel(), actor.correlationId()));
        support.audit().record(actor, command.channel(), AuditAction.CLEANING_CHECKLIST_ITEM_RECORDED,
                "CleaningTaskChecklistItem", recorded.id().toString(), recorded.siteCode(), item, recorded);
        return recorded;
    }

    /**
     * Completes the task - SRS-SFL-S169-02: "cannot be marked complete with an unaddressed required
     * checklist item or missing required photo evidence".
     */
    @Transactional
    public CleaningTask complete(CleaningCommands.CompleteTask command) {
        ActorContext actor = command.actor();
        CleaningTask task = support.requireTask(command.taskId());
        support.assertVisible(actor, task, command.channel());
        support.requireMayExecute(actor, task, command.channel());
        task.metadata().requireVersion(command.expectedVersion(), "Cleaning task", task.id());
        if (task.status() != TaskStatus.IN_PROGRESS) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Only a cleaning task in progress can be completed; this one is " + task.status() + ".");
        }
        List<ChecklistCompletionPolicy.Unaddressed> problems =
                ChecklistCompletionPolicy.unaddressed(repository.findChecklistItems(task.id()));
        if (!problems.isEmpty()) {
            Map<String, Object> facts = new LinkedHashMap<>();
            facts.put("taskNumber", task.taskNumber());
            facts.put("unaddressedItems", problems.stream().map(ChecklistCompletionPolicy.Unaddressed::itemCode)
                    .toList());
            support.refusals().record(actor, command.channel(), AuditAction.CLEANING_TASK_COMPLETION_REFUSED,
                    "CleaningTask", task.id().toString(), task.siteCode(), facts);
            throw new FacilitiesException(FacilitiesErrorCode.CLEANING_CHECKLIST_INCOMPLETE,
                    ChecklistCompletionPolicy.describe(problems));
        }
        Instant at = support.now();
        Long discrepancy = SlaCompliancePolicy.discrepancy(at, command.vendorReportedCompletedAt(),
                support.configuration().discrepancyTolerance(task.siteCode())).orElse(null);
        CleaningTask completed = repository.saveTask(task.complete(command.notes(),
                command.vendorReportedCompletedAt(), discrepancy, actor.actorId(), at, command.channel(),
                actor.correlationId()));
        support.audit().record(actor, command.channel(), AuditAction.CLEANING_TASK_COMPLETED, "CleaningTask",
                completed.id().toString(), completed.siteCode(), task, completed);
        if (discrepancy != null) {
            Map<String, Object> facts = new LinkedHashMap<>();
            facts.put("recordedCompletedAt", at.toString());
            facts.put("vendorReportedCompletedAt", command.vendorReportedCompletedAt().toString());
            facts.put("discrepancySeconds", discrepancy);
            facts.put("appliedToSla", "recordedCompletedAt");
            support.audit().record(actor, command.channel(), AuditAction.CLEANING_COMPLETION_DISCREPANCY_RECORDED,
                    "CleaningTask", completed.id().toString(), completed.siteCode(), null, facts);
        }
        support.publishTask("sfl.ifimp.cleaning-task-completed.v1", completed, actor);
        sla.evaluateTimes(completed, actor, command.channel());
        return completed;
    }

    /**
     * Cancels with a reason. A supervisor may cancel anything live; an occupant may withdraw their own
     * reactive request while nobody has been assigned to it yet.
     */
    @Transactional
    public CleaningTask cancel(CleaningCommands.CancelTask command) {
        ActorContext actor = command.actor();
        CleaningTask task = support.requireTask(command.taskId());
        support.authorization().requireSite(actor, task.siteCode(), command.channel(), "CleaningTask",
                task.id().toString());
        boolean ownOpenRequest = task.origin() == TaskOrigin.REACTIVE && task.status() == TaskStatus.OPEN
                && actor.actorId().equals(task.requestedBy())
                && support.authorization().has(actor, SflPermission.FACILITIES_CLEANING_REQUEST);
        if (!ownOpenRequest) {
            support.authorization().require(actor, SflPermission.FACILITIES_CLEANING_TASK_SUPERVISE, task.siteCode(),
                    command.channel(), "CleaningTask", task.id().toString());
        }
        task.metadata().requireVersion(command.expectedVersion(), "Cleaning task", task.id());
        return cancelTask(task, command.reason(), actor, command.channel());
    }

    /** Shared with the booking and capacity paths, which cancel on the booking's or S173's behalf. */
    CleaningTask cancelTask(CleaningTask task, String reason, ActorContext actor, SourceChannel channel) {
        CleaningTask cancelled = repository.saveTask(task.cancel(reason, actor.actorId(), support.now(), channel,
                actor.correlationId()));
        support.audit().record(actor, channel, AuditAction.CLEANING_TASK_CANCELLED, "CleaningTask",
                cancelled.id().toString(), cancelled.siteCode(), task, cancelled);
        support.publishTask("sfl.ifimp.cleaning-task-cancelled.v1", cancelled, actor);
        return cancelled;
    }

    // =============================================================================================
    // Queries
    // =============================================================================================

    @Transactional(readOnly = true)
    public CleaningTask find(UUID taskId, ActorContext actor, SourceChannel channel) {
        CleaningTask task = support.requireTask(taskId);
        support.assertVisible(actor, task, channel);
        return task;
    }

    @Transactional(readOnly = true)
    public List<TaskChecklistItem> checklist(UUID taskId, ActorContext actor, SourceChannel channel) {
        return repository.findChecklistItems(find(taskId, actor, channel).id());
    }

    @Transactional(readOnly = true)
    public List<SlaBreach> breaches(UUID taskId, ActorContext actor, SourceChannel channel) {
        CleaningTask task = find(taskId, actor, channel);
        support.requireUnnarrowedRead(actor, task.siteCode(), channel, "CleaningSlaBreach");
        return repository.findBreachesForTask(task.id());
    }

    /**
     * The task search, narrowed per record: a vendor technician gets their assignments, an occupant their
     * own requests, whatever filters they asked for.
     */
    @Transactional(readOnly = true)
    public RepositoryPage<CleaningTask> search(CleaningRepository.TaskQuery query, ActorContext actor,
            SourceChannel channel) {
        support.authorization().requireRequestedSite(actor, query.siteCode(), channel, "CleaningTask");
        CleaningSupport.Narrowing narrowing = support.readNarrowing(actor, query.siteCode(), channel, "list");
        CleaningRepository.TaskQuery effective = switch (narrowing.kind()) {
            case NONE -> query;
            case ASSIGNED_TO_ME -> new CleaningRepository.TaskQuery(query.siteCode(), query.roomId(), query.status(),
                    query.origin(), query.requestedBy(), narrowing.actorId(), query.bookingId(), query.from(),
                    query.to(), query.page(), query.size());
            case REQUESTED_BY_ME -> new CleaningRepository.TaskQuery(query.siteCode(), query.roomId(),
                    query.status(), query.origin(), narrowing.actorId(), query.assignedTo(), query.bookingId(),
                    query.from(), query.to(), query.page(), query.size());
        };
        RepositoryPage<CleaningTask> found = repository.findTasks(effective);
        List<CleaningTask> visible = support.authorization().filterBySite(actor, found.items(),
                CleaningTask::siteCode);
        return visible.size() == found.items().size() ? found
                : RepositoryPage.of(visible, visible.size(), found.page(), found.size());
    }

    // =============================================================================================
    // Internals
    // =============================================================================================

    /** A registered, active vendor at the site - or CLEANING_VENDOR_NOT_FOUND. */
    CleaningVendor requireAssignableVendor(UUID vendorId, String siteCode) {
        CleaningVendor vendor = vendorId == null ? null
                : repository.findVendor(vendorId).filter(found -> found.siteCode().equals(siteCode)).orElse(null);
        if (vendor == null) {
            throw new FacilitiesException(FacilitiesErrorCode.CLEANING_VENDOR_NOT_FOUND,
                    "No cleaning vendor " + vendorId + " is registered at " + siteCode
                            + " from Vendor Master (S133).");
        }
        if (!vendor.assignable()) {
            throw new FacilitiesException.ValidationFailedException(
                    "Vendor " + vendor.name() + " is " + vendor.status() + " and cannot be given new work.");
        }
        return vendor;
    }

    private Optional<CleaningTask> replay(String operation, String key, Object payload) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return idempotency.findExistingResult(operation, key, idempotency.fingerprint(payload))
                .flatMap(repository::findTask);
    }

    private void remember(String operation, String key, Object payload, CleaningTask task, ActorContext actor) {
        idempotency.recordResult(operation, key, idempotency.fingerprint(payload), task.id(), task.siteCode(),
                actor.actorId());
    }
}
