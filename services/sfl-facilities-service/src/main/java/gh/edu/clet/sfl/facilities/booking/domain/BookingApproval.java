package gh.edu.clet.sfl.facilities.booking.domain;

import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A decision on a booking request — SRS-SFL-S159-01, "approval".
 *
 * <p>A record rather than a status, because the interesting content is who decided and why, and
 * because a booking that was approved and one that never needed approving are different facts a
 * single {@code CONFIRMED} status cannot tell apart. The presence of one of these is the difference.
 *
 * <p>A rejection requires a reason; an approval does not. The asymmetry is deliberate — the requester
 * of a rejected booking has to be told something, and nobody has ever needed an explanation for being
 * given the room they asked for.
 */
public record BookingApproval(
        UUID id,
        UUID bookingId,
        String siteCode,
        ApprovalDecision decision,
        String reason,
        String decidedBy,
        Instant decidedAt) {

    public BookingApproval {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(bookingId, "bookingId is required");
        siteCode = EstateCodes.normalize(siteCode);
        Objects.requireNonNull(decision, "decision is required");
        reason = EstateCodes.blankToNull(reason);
        EstateCodes.require(decidedBy, "decidedBy");
        decidedBy = decidedBy.strip();
        Objects.requireNonNull(decidedAt, "decidedAt is required");
        if (decision == ApprovalDecision.REJECTED && reason == null) {
            throw new FacilitiesException.ValidationFailedException("A rejected booking must say why.");
        }
    }

    public static BookingApproval decide(UUID id, Booking booking, ApprovalDecision decision,
            String reason, String actorId, Instant at) {
        return new BookingApproval(id, booking.id(), booking.siteCode(), decision, reason, actorId, at);
    }
}
