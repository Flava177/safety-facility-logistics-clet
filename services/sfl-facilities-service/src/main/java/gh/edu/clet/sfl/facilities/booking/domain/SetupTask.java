package gh.edu.clet.sfl.facilities.booking.domain;

import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Something that must be done to a room before a booking can use it.
 *
 * <h2>Why this is not an S153 work order</h2>
 *
 * The obvious move is to raise a maintenance work order and get the queue, the SLA and the closure
 * evidence for free. It is the wrong move, and the reason is what would end up in that queue.
 *
 * <p>A setup task is a twenty-minute room turnaround — chairs into examination layout, a projector
 * wheeled in, water on the table. Routing that through the CMMS would put it in the same queue as a
 * failed standby generator, give it an escalation ladder, and demand closure evidence before anybody
 * could say the chairs were straight. The queue would fill with turnarounds and the generator would
 * be on page four.
 *
 * <p>So this stays deliberately thin: what, by when, done or not, and who did it. If a setup reveals
 * something actually broken, that is a fault, and S153 already has a register for it.
 *
 * @param dueBy when the room must be ready. Defaults to the start of the occupied window — allowing
 *        for the setup buffer — rather than to the booking start itself.
 */
public record SetupTask(
        UUID id,
        UUID bookingId,
        UUID roomId,
        String siteCode,
        String description,
        Instant dueBy,
        SetupTaskStatus status,
        String assignedTo,
        String completedBy,
        Instant completedAt,
        String notes) {

    public SetupTask {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(bookingId, "bookingId is required");
        Objects.requireNonNull(roomId, "roomId is required");
        siteCode = EstateCodes.normalize(siteCode);
        EstateCodes.require(description, "description");
        description = description.strip();
        Objects.requireNonNull(dueBy, "dueBy is required");
        Objects.requireNonNull(status, "status is required");
        assignedTo = EstateCodes.blankToNull(assignedTo);
        completedBy = EstateCodes.blankToNull(completedBy);
        notes = EstateCodes.blankToNull(notes);
    }

    public static SetupTask create(UUID id, Booking booking, String description, Instant dueBy,
            String assignedTo) {
        return new SetupTask(id, booking.id(), booking.roomId(), booking.siteCode(), description,
                dueBy == null ? booking.window().occupied().start() : dueBy, SetupTaskStatus.PENDING,
                assignedTo, null, null, null);
    }

    /** Marks the task done or deliberately skipped. Skipping requires a reason. */
    public SetupTask resolve(SetupTaskStatus outcome, String resolutionNotes, String actorId,
            Instant at) {
        if (outcome != SetupTaskStatus.DONE && outcome != SetupTaskStatus.SKIPPED) {
            throw new FacilitiesException.InvalidStateTransitionException(outcome + " is not a resolution.");
        }
        if (status != SetupTaskStatus.PENDING) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "This setup task is already " + status + ".");
        }
        if (outcome == SetupTaskStatus.SKIPPED && (resolutionNotes == null || resolutionNotes.isBlank())) {
            throw new FacilitiesException.ValidationFailedException(
                    "A skipped setup task must say why, or it cannot be told from one nobody got to.");
        }
        return new SetupTask(id, bookingId, roomId, siteCode, description, dueBy, outcome, assignedTo,
                actorId, at, resolutionNotes);
    }

    /** {@code true} when the room should already be ready and is not. */
    public boolean isOverdue(Instant now) {
        return status == SetupTaskStatus.PENDING && now.isAfter(dueBy);
    }
}
