package gh.edu.clet.sfl.facilities.cleaning.domain;

import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One clean of one room in one window - the unit every S169 requirement is stated against.
 *
 * <h2>The window is the commitment</h2>
 *
 * {@code [windowStart, dueBy)} is half-open, like an S159 booking window, and it is what S173 capacity
 * counts (SRS-SFL-S169-04). A routine task's window is its schedule occurrence; a booking setup task
 * ends at the booking's occupied start; a teardown task starts at the booked end; a reactive request
 * starts when it was raised and is due after the configured target.
 *
 * <h2>Whose timestamps count</h2>
 *
 * {@code startedAt} and {@code completedAt} are set by this record's own transitions from the service
 * clock, never from a request body. {@code vendorReportedCompletedAt} is what a vendor said, stored
 * beside them and never copied into them - SRS-SFL-S169-03: an SLA breach "cannot be overridden by a
 * vendor-supplied completion time that conflicts with" the task's own. When the two disagree beyond
 * the configured tolerance the difference is kept in {@code completionDiscrepancySeconds}, so the
 * conflict is itself on the record rather than silently resolved.
 *
 * @param bookingId the S159 back-reference (SRS-SFL-S169-01 validation), by value; required, together
 *        with {@code bookingReference}, whenever {@link TaskOrigin#claimsBooking()}
 * @param reservationId the S169-04 capacity reservation this task was raised for, when S173 raised it
 * @param requestedBy the occupant who raised a reactive request, or the actor/system that raised any
 *        other task. The per-record narrowing of an IFIMP_REQUESTER is on this field.
 * @param assignedTo the in-house cleaner or vendor technician; a VENDOR_TECHNICIAN sees only tasks where
 *        this is them
 */
public record CleaningTask(
        UUID id,
        String taskNumber,
        String siteCode,
        UUID roomId,
        String roomCode,
        SpaceType spaceType,
        TaskOrigin origin,
        String title,
        String description,
        UUID scheduleId,
        Instant occurrenceStart,
        UUID bookingId,
        String bookingReference,
        UUID reservationId,
        String eventReference,
        Instant windowStart,
        Instant dueBy,
        TaskStatus status,
        String requestedBy,
        Instant requestedAt,
        AssigneeType assigneeType,
        String assignedTo,
        UUID vendorId,
        Instant assignedAt,
        Instant startedAt,
        Instant completedAt,
        String completedBy,
        String completionNotes,
        Instant vendorReportedCompletedAt,
        Long completionDiscrepancySeconds,
        Instant cancelledAt,
        String cancellationReason,
        UUID checklistTemplateId,
        Integer checklistTemplateVersion,
        RecordMetadata metadata) {

    public CleaningTask {
        Objects.requireNonNull(id, "id is required");
        EstateCodes.require(taskNumber, "taskNumber");
        siteCode = EstateCodes.normalize(siteCode);
        Objects.requireNonNull(roomId, "roomId is required");
        roomCode = EstateCodes.normalize(roomCode);
        Objects.requireNonNull(spaceType, "spaceType is required");
        Objects.requireNonNull(origin, "origin is required");
        if (title == null || title.isBlank()) {
            throw new FacilitiesException.ValidationFailedException("A cleaning task needs a title.");
        }
        title = title.strip();
        description = EstateCodes.blankToNull(description);
        Objects.requireNonNull(windowStart, "windowStart is required");
        Objects.requireNonNull(dueBy, "dueBy is required");
        if (!dueBy.isAfter(windowStart)) {
            throw new FacilitiesException.ValidationFailedException("A cleaning task must be due after it starts.");
        }
        Objects.requireNonNull(status, "status is required");
        EstateCodes.require(requestedBy, "requestedBy");
        Objects.requireNonNull(requestedAt, "requestedAt is required");
        if (origin.claimsBooking() && (bookingId == null || bookingReference == null || bookingReference.isBlank())) {
            // The database says the same (ck_cleaning_tasks_booking_reference). Refusing here means the
            // failure names the rule rather than a constraint.
            throw new FacilitiesException(
                    gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode.CLEANING_BOOKING_UNLINKED);
        }
        if (origin == TaskOrigin.ROUTINE && (scheduleId == null || occurrenceStart == null)) {
            throw new IllegalArgumentException("a routine task carries its schedule occurrence");
        }
        if (origin == TaskOrigin.EVENT && (eventReference == null || eventReference.isBlank())) {
            throw new IllegalArgumentException("an event task carries its S173 reference");
        }
        if (assigneeType == AssigneeType.VENDOR && vendorId == null) {
            throw new IllegalArgumentException("a vendor assignment names the vendor");
        }
        completionNotes = EstateCodes.blankToNull(completionNotes);
        cancellationReason = EstateCodes.blankToNull(cancellationReason);
        Objects.requireNonNull(metadata, "metadata is required");
    }

    /**
     * Everything a newly raised task needs; the lifecycle fields all start empty.
     *
     * @param checklistTemplateId null when no active template exists for the space type - the task then
     *        carries no checklist, and the dashboard says so rather than the task silently passing
     */
    public record Raise(UUID id, String taskNumber, String siteCode, UUID roomId, String roomCode, SpaceType spaceType,
            TaskOrigin origin, String title, String description, UUID scheduleId, Instant occurrenceStart,
            UUID bookingId, String bookingReference, UUID reservationId, String eventReference, Instant windowStart,
            Instant dueBy, String requestedBy, UUID checklistTemplateId, Integer checklistTemplateVersion) {
    }

    public static CleaningTask raise(Raise raise, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new CleaningTask(raise.id(), raise.taskNumber(), raise.siteCode(), raise.roomId(), raise.roomCode(),
                raise.spaceType(), raise.origin(), raise.title(), raise.description(), raise.scheduleId(),
                raise.occurrenceStart(), raise.bookingId(), raise.bookingReference(), raise.reservationId(),
                raise.eventReference(), raise.windowStart(), raise.dueBy(), TaskStatus.OPEN, raise.requestedBy(), at,
                null, null, null, null, null, null, null, null, null, null, null, null, raise.checklistTemplateId(),
                raise.checklistTemplateVersion(), RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** Assigns, or reassigns before anybody has started. */
    public CleaningTask assign(AssigneeType type, String assignee, UUID vendor, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        if (type == null) {
            throw new FacilitiesException.ValidationFailedException("Say whether the task goes to staff or a vendor.");
        }
        if (assignee == null || assignee.isBlank()) {
            throw new FacilitiesException.ValidationFailedException(
                    "Name the cleaner or vendor technician the task is assigned to.");
        }
        if (type == AssigneeType.STAFF && vendor != null) {
            throw new FacilitiesException.ValidationFailedException("An in-house assignment does not name a vendor.");
        }
        Draft draft = draft();
        draft.status = status.transitionTo(TaskStatus.ASSIGNED);
        draft.assigneeType = type;
        draft.assignedTo = assignee.strip();
        draft.vendorId = type == AssigneeType.VENDOR ? vendor : null;
        draft.assignedAt = at;
        return draft.build(metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** The assignee has arrived. Stops the SLA response clock (SRS-SFL-S169-03). */
    public CleaningTask start(String actorId, Instant at, SourceChannel channel, String correlationId) {
        Draft draft = draft();
        draft.status = status.transitionTo(TaskStatus.IN_PROGRESS);
        draft.startedAt = at;
        return draft.build(metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /**
     * Completes the task at {@code at} - the service clock, always.
     *
     * <p>The checklist gate is not here: it needs the task's checklist items, which are their own rows.
     * The application service runs {@code ChecklistCompletionPolicy} first and only then calls this.
     *
     * @param vendorReported what the vendor claims, or null; stored, never used as {@code completedAt}
     * @param discrepancySeconds {@code vendorReported - at} when outside tolerance, else null
     */
    public CleaningTask complete(String notes, Instant vendorReported, Long discrepancySeconds, String actorId,
            Instant at, SourceChannel channel, String correlationId) {
        Draft draft = draft();
        draft.status = status.transitionTo(TaskStatus.COMPLETED);
        draft.completedAt = at;
        draft.completedBy = actorId;
        draft.completionNotes = notes;
        draft.vendorReportedCompletedAt = vendorReported;
        draft.completionDiscrepancySeconds = discrepancySeconds;
        return draft.build(metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** Cancelled with a reason, which is required whoever cancels - a booking withdrawal says why. */
    public CleaningTask cancel(String reason, String actorId, Instant at, SourceChannel channel, String correlationId) {
        if (reason == null || reason.isBlank()) {
            throw new FacilitiesException.ValidationFailedException("A cancellation reason is required.");
        }
        Draft draft = draft();
        draft.status = status.transitionTo(TaskStatus.CANCELLED);
        draft.cancelledAt = at;
        draft.cancellationReason = reason.strip();
        return draft.build(metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** Follows a rescheduled booking. Only a live task moves; a completed clean stays where it happened. */
    public CleaningTask moveWindow(Instant newStart, Instant newDueBy, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        if (!status.isLive()) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "A " + status + " cleaning task cannot be moved.");
        }
        Draft draft = draft();
        draft.windowStart = newStart;
        draft.dueBy = newDueBy;
        return draft.build(metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public boolean isLive() {
        return status.isLive();
    }

    /** Still outstanding after its due time - the dashboard's "overdue reactive requests" when reactive. */
    public boolean isOverdueAt(Instant now) {
        return status.isLive() && now.isAfter(dueBy);
    }

    /** Half-open overlap of the task window with {@code [from, to)}. */
    public boolean overlaps(Instant from, Instant to) {
        return windowStart.isBefore(to) && from.isBefore(dueBy);
    }

    public boolean isAssignedTo(String actorId) {
        return assignedTo != null && assignedTo.equals(actorId);
    }

    private Draft draft() {
        return new Draft(this);
    }

    /**
     * A mutable copy for the transitions above. Thirty-five components make a hand-written canonical
     * constructor call per transition unreadable, and an unreadable transition is where a field gets
     * silently dropped; one copy, changed field by field, keeps every transition to what it changes.
     */
    private static final class Draft {
        private final CleaningTask from;
        private Instant windowStart;
        private Instant dueBy;
        private TaskStatus status;
        private AssigneeType assigneeType;
        private String assignedTo;
        private UUID vendorId;
        private Instant assignedAt;
        private Instant startedAt;
        private Instant completedAt;
        private String completedBy;
        private String completionNotes;
        private Instant vendorReportedCompletedAt;
        private Long completionDiscrepancySeconds;
        private Instant cancelledAt;
        private String cancellationReason;

        Draft(CleaningTask task) {
            this.from = task;
            windowStart = task.windowStart;
            dueBy = task.dueBy;
            status = task.status;
            assigneeType = task.assigneeType;
            assignedTo = task.assignedTo;
            vendorId = task.vendorId;
            assignedAt = task.assignedAt;
            startedAt = task.startedAt;
            completedAt = task.completedAt;
            completedBy = task.completedBy;
            completionNotes = task.completionNotes;
            vendorReportedCompletedAt = task.vendorReportedCompletedAt;
            completionDiscrepancySeconds = task.completionDiscrepancySeconds;
            cancelledAt = task.cancelledAt;
            cancellationReason = task.cancellationReason;
        }

        CleaningTask build(RecordMetadata metadata) {
            return new CleaningTask(from.id, from.taskNumber, from.siteCode, from.roomId, from.roomCode,
                    from.spaceType, from.origin, from.title, from.description, from.scheduleId, from.occurrenceStart,
                    from.bookingId, from.bookingReference, from.reservationId, from.eventReference, windowStart,
                    dueBy, status, from.requestedBy, from.requestedAt, assigneeType, assignedTo, vendorId,
                    assignedAt, startedAt, completedAt, completedBy, completionNotes, vendorReportedCompletedAt,
                    completionDiscrepancySeconds, cancelledAt, cancellationReason, from.checklistTemplateId,
                    from.checklistTemplateVersion, metadata);
        }
    }
}
