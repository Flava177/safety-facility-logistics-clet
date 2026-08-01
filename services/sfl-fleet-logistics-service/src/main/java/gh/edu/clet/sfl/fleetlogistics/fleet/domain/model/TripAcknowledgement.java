package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * The assigned driver's answer to a trip assignment.
 *
 * <p>Kept as a value object rather than four loose columns on {@link Trip} so the invariant — a
 * deferral has a reason, a pending acknowledgement has neither actor nor timestamp — lives in one
 * place and cannot be half-applied by a caller that sets the state and forgets the reason.
 *
 * @param state who has answered and how
 * @param reason why the driver deferred; null unless {@code state} is
 *        {@link TripAcknowledgementState#DEFERRED}
 * @param answeredAt when the driver answered; null while pending
 * @param answeredBy the identity that answered — the actor's subject, so the record survives a rename
 */
public record TripAcknowledgement(
        TripAcknowledgementState state,
        String reason,
        Instant answeredAt,
        String answeredBy) {

    private static final int MAX_REASON_LENGTH = 1000;

    public TripAcknowledgement {
        Objects.requireNonNull(state, "state is required");
        reason = reason == null || reason.isBlank() ? null : reason.strip();

        if (state.requiresReason() && reason == null) {
            throw new IllegalArgumentException("A deferral reason is required");
        }
        if (reason != null && reason.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException(
                    "A deferral reason cannot exceed " + MAX_REASON_LENGTH + " characters");
        }
        // A reason on a confirmation is dropped rather than stored: it would appear in the interface
        // under a "deferral reason" heading on a trip that was not deferred.
        if (state == TripAcknowledgementState.CONFIRMED) {
            reason = null;
        }
        if (state.isAnswered()) {
            Objects.requireNonNull(answeredAt, "answeredAt is required once a driver has answered");
            if (answeredBy == null || answeredBy.isBlank()) {
                throw new IllegalArgumentException("answeredBy is required once a driver has answered");
            }
            answeredBy = answeredBy.strip();
        }
    }

    /** The state a newly assigned trip starts in. */
    public static TripAcknowledgement pending() {
        return new TripAcknowledgement(TripAcknowledgementState.PENDING, null, null, null);
    }

    public static TripAcknowledgement confirmedBy(String actor, Instant at) {
        return new TripAcknowledgement(TripAcknowledgementState.CONFIRMED, null, at, actor);
    }

    public static TripAcknowledgement deferredBy(String actor, String reason, Instant at) {
        return new TripAcknowledgement(TripAcknowledgementState.DEFERRED, reason, at, actor);
    }

    public boolean isPending() {
        return state == TripAcknowledgementState.PENDING;
    }
}
