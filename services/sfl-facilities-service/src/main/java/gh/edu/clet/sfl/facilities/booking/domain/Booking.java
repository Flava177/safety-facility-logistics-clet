package gh.edu.clet.sfl.facilities.booking.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A reservation of a space for a window — SRS-SFL-S159-01.
 *
 * <h2>What a booking is a promise about</h2>
 *
 * That a named space will be usable, by a named person, between two times. Every rule in this module
 * exists because one of those three can stop being true: the space can be double-booked, it can
 * become unusable, or nobody can turn up.
 *
 * <h2>The readiness hold is a flag, not a state</h2>
 *
 * {@code readinessHoldReason} sits beside the status rather than inside it, and that is deliberate.
 * A confirmed booking on a space that has just been blocked is <em>still a confirmed booking</em> —
 * somebody has it in their diary and is planning around it. Moving it to a state called
 * {@code AT_RISK} would mean deciding, on the estate's behalf, that a hall blocked on Tuesday will
 * still be blocked on Friday. It usually will not be.
 *
 * <p>So the booking keeps its status and gains a visible reason, the space keeps its own readiness,
 * and a human decides whether to move the booking. When the space recovers the flag clears with no
 * state change and nobody has to be told twice.
 *
 * @param window the booked times, and the buffers around them. Conflict is tested on
 *        {@link BookingWindow#occupied()}, never on the bare window.
 * @param approvalRequired resolved at request time from runtime configuration and stored, so a rule
 *        changed while a booking sits in the queue does not retrospectively make it un-approvable.
 * @param overrideReason set when somebody with the override permission booked into a space readiness
 *        said was unavailable. Null on an ordinary booking, and its presence is the audit trail.
 */
public record Booking(
        UUID id,
        String bookingReference,
        String siteCode,
        UUID roomId,
        String roomCode,
        BookingPurpose purpose,
        String title,
        String description,
        BookingWindow window,
        BookingStatus status,
        int expectedAttendees,
        String requestedBy,
        String requestedFor,
        Instant requestedAt,
        boolean approvalRequired,
        UUID approvalId,
        Instant confirmedAt,
        Instant startedAt,
        Instant completedAt,
        String closureReason,
        ReadinessHoldReason readinessHoldReason,
        Instant readinessHeldAt,
        String overrideReason,
        RecordLifecycleStatus lifecycleStatus,
        RecordMetadata metadata) {

    public Booking {
        Objects.requireNonNull(id, "id is required");
        bookingReference = EstateCodes.normalize(bookingReference);
        siteCode = EstateCodes.normalize(siteCode);
        Objects.requireNonNull(roomId, "roomId is required");
        roomCode = EstateCodes.normalize(roomCode);
        Objects.requireNonNull(purpose, "purpose is required");
        EstateCodes.require(title, "title");
        title = title.strip();
        description = EstateCodes.blankToNull(description);
        Objects.requireNonNull(window, "window is required");
        Objects.requireNonNull(status, "status is required");
        EstateCodes.require(requestedBy, "requestedBy");
        requestedBy = requestedBy.strip();
        requestedFor = EstateCodes.blankToNull(requestedFor);
        Objects.requireNonNull(requestedAt, "requestedAt is required");
        closureReason = EstateCodes.blankToNull(closureReason);
        overrideReason = EstateCodes.blankToNull(overrideReason);
        Objects.requireNonNull(lifecycleStatus, "lifecycleStatus is required");
        Objects.requireNonNull(metadata, "metadata is required");
        if (expectedAttendees < 0) {
            throw new IllegalArgumentException("expectedAttendees cannot be negative");
        }
    }

    /** A newly requested booking. Already holding the space — see {@link BookingStatus}. */
    public static Booking request(UUID id, String bookingReference, String siteCode, UUID roomId,
            String roomCode, BookingPurpose purpose, String title, String description,
            BookingWindow window, int expectedAttendees, String requestedFor, boolean approvalRequired,
            String overrideReason, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new Booking(id, bookingReference, siteCode, roomId, roomCode, purpose, title, description,
                window, BookingStatus.REQUESTED, expectedAttendees, actorId, requestedFor, at,
                approvalRequired, null, null, null, null, null, null, null, overrideReason,
                RecordLifecycleStatus.ACTIVE,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /**
     * Confirms the booking.
     *
     * <p>{@code approvalId} is null for a booking that needed none, and its absence is what records
     * that fact — there is no separate "did not need approving" flag to fall out of step with the
     * {@code approvalRequired} the booking was created with.
     */
    public Booking confirm(UUID approval, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        if (approvalRequired && approval == null) {
            throw new FacilitiesException.UnauthorizedApprovalException(
                    "This booking requires approval before it can be confirmed.");
        }
        BookingStatus next = status.transitionTo(BookingStatus.CONFIRMED);
        return copy(next, approval, at, startedAt, completedAt, closureReason, readinessHoldReason,
                readinessHeldAt, actorId, at, channel, correlationId);
    }

    /** Refused by an approver. The reason is required and is what the requester will read. */
    public Booking reject(UUID approval, String reason, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        EstateCodes.require(reason, "reason");
        BookingStatus next = status.transitionTo(BookingStatus.REJECTED);
        return copy(next, approval, confirmedAt, startedAt, at, reason.strip(), null, null, actorId, at,
                channel, correlationId);
    }

    /** Somebody has arrived and taken the room. Releases any readiness hold: they are in it. */
    public Booking start(String actorId, Instant at, SourceChannel channel, String correlationId) {
        BookingStatus next = status.transitionTo(BookingStatus.IN_USE);
        return copy(next, approvalId, confirmedAt, at, completedAt, closureReason, null, null, actorId,
                at, channel, correlationId);
    }

    public Booking complete(String notes, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        BookingStatus next = status.transitionTo(BookingStatus.COMPLETED);
        return copy(next, approvalId, confirmedAt, startedAt, at, notes, null, null, actorId, at,
                channel, correlationId);
    }

    /** Withdrawn before use. A reason is required whoever cancels and however late. */
    public Booking cancel(String reason, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        EstateCodes.require(reason, "reason");
        BookingStatus next = status.transitionTo(BookingStatus.CANCELLED);
        return copy(next, approvalId, confirmedAt, startedAt, at, reason.strip(), null, null, actorId,
                at, channel, correlationId);
    }

    /**
     * Marks the booking as never used.
     *
     * <p>Reached only from the scheduled sweep. Package-visible reasoning: "they did not turn up" is
     * an observation about a window that has passed, and the sweep is the only thing positioned to
     * make it without it becoming an accusation somebody typed.
     */
    public Booking markNoShow(String actorId, Instant at, SourceChannel channel, String correlationId) {
        BookingStatus next = status.transitionTo(BookingStatus.NO_SHOW);
        return copy(next, approvalId, confirmedAt, startedAt, at,
                "No attendance recorded before the booking window closed.", null, null, actorId, at,
                channel, correlationId);
    }

    /**
     * Places or clears a readiness hold.
     *
     * <p>Does not touch the status, and does not bump the version. The flag mirrors a decision the
     * readiness module made about the space; it is not an edit somebody performed on the booking, and
     * versioning it would make every reconciliation sweep collide with a concurrent cancellation.
     */
    public Booking withReadinessHold(ReadinessHoldReason reason, Instant at) {
        if (reason == readinessHoldReason) {
            return this;
        }
        return new Booking(id, bookingReference, siteCode, roomId, roomCode, purpose, title, description,
                window, status, expectedAttendees, requestedBy, requestedFor, requestedAt,
                approvalRequired, approvalId, confirmedAt, startedAt, completedAt, closureReason,
                reason, reason == null ? null : at, overrideReason, lifecycleStatus, metadata);
    }

    /**
     * Moves the booking to a new window.
     *
     * <p>Refused once the booking is {@link BookingStatus#IN_USE}: people are in the room, and the
     * honest move is to complete it and book again rather than to rewrite when it started.
     *
     * <p>A move clears any readiness hold. The hold was a statement about a specific window on a
     * specific space; carrying it across to a different window would assert something nothing has
     * checked. The reconciliation sweep re-places it within the minute if it still applies.
     *
     * <p>Approval is <em>not</em> reset. That is a deliberate call and the arguable one: moving an
     * approved booking by ten minutes does not warrant sending it back round the approver, and a site
     * that disagrees should cancel and re-request rather than have every reschedule silently drop out
     * of the diary it is already in.
     */
    public Booking reschedule(BookingWindow newWindow, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        Objects.requireNonNull(newWindow, "newWindow is required");
        if (!status.holdsTheSpace()) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "A " + status + " booking cannot be moved.");
        }
        if (status == BookingStatus.IN_USE) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "This booking is in use. Complete it and raise a new one rather than moving it.");
        }
        return new Booking(id, bookingReference, siteCode, roomId, roomCode, purpose, title, description,
                newWindow, status, expectedAttendees, requestedBy, requestedFor, requestedAt,
                approvalRequired, approvalId, confirmedAt, startedAt, completedAt, closureReason,
                null, null, overrideReason, lifecycleStatus,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public Booking changeLifecycle(RecordLifecycleStatus target, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        RecordLifecycleStatus next = lifecycleStatus.transitionTo(target, "Booking");
        return new Booking(id, bookingReference, siteCode, roomId, roomCode, purpose, title, description,
                window, status, expectedAttendees, requestedBy, requestedFor, requestedAt,
                approvalRequired, approvalId, confirmedAt, startedAt, completedAt, closureReason,
                readinessHoldReason, readinessHeldAt, overrideReason, next,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** {@code true} when this booking currently occupies its space. */
    public boolean holdsTheSpace() {
        return status.holdsTheSpace();
    }

    /**
     * {@code true} when the sweep should call this a no-show.
     *
     * <p>Measured from the <em>start</em> plus a grace period, not from the end of the window. A
     * three-hour lecture nobody attended should not hold a hall for three hours — releasing it twenty
     * minutes in is what makes a no-show policy worth having rather than a statistic collected after
     * the fact.
     *
     * <p>The consequence, which is real and is why the grace is configurable: arriving after it finds
     * the booking gone, because {@link BookingStatus#NO_SHOW} is terminal. A site that habitually
     * starts late should raise the grace rather than work around it.
     */
    public boolean isNoShowAt(Instant now, java.time.Duration grace) {
        return status == BookingStatus.CONFIRMED
                && startedAt == null
                && now.isAfter(window.start().plus(grace == null ? java.time.Duration.ZERO : grace));
    }

    /** {@code true} when somebody booked past a readiness refusal. Always worth surfacing. */
    public boolean wasOverridden() {
        return overrideReason != null;
    }

    private Booking copy(BookingStatus newStatus, UUID newApprovalId, Instant newConfirmedAt,
            Instant newStartedAt, Instant newCompletedAt, String newClosureReason,
            ReadinessHoldReason newHold, Instant newHeldAt, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new Booking(id, bookingReference, siteCode, roomId, roomCode, purpose, title, description,
                window, newStatus, expectedAttendees, requestedBy, requestedFor, requestedAt,
                approvalRequired, newApprovalId, newConfirmedAt, newStartedAt, newCompletedAt,
                newClosureReason, newHold, newHeldAt, overrideReason, lifecycleStatus,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }
}
