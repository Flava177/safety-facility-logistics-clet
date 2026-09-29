package gh.edu.clet.sfl.facilities.cleaning.application.contract;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * S169's schedule and capacity, as S173 event logistics draws on it - SRS-SFL-S169-04.
 *
 * <p>An event's cleaning is resourced "the same way its AV and security needs are": S173 asks, S169
 * answers at request time with either a reservation or a conflict - and a conflict must "name the
 * competing commitment, not just state that none is available". That is why {@link Reservation} carries
 * the competing commitment's identity and window rather than a boolean.
 */
public interface EventCleaningCapacity {

    /** Reserves cleaning capacity for an event window, or returns the conflict that prevents it. */
    Reservation reserve(ReservationRequest request);

    Optional<Reservation> find(UUID reservationId);

    /** The event no longer needs it - cancelled, or re-planned. Idempotent. */
    void release(UUID reservationId, String reason);

    /**
     * @param eventReference S173's own set-up task / resource request id, held by S169 by value
     */
    record ReservationRequest(String siteCode, java.util.UUID roomId, String locationCode, Instant from, Instant to,
            String scope, String eventReference, String requestedBy) {
    }

    enum Status {
        /** Capacity committed and a cleaning task raised. */
        RESERVED,
        /** Refused at request time; the competing commitment is named. */
        CONFLICT,
        /** The cleaning task it raised has been completed. */
        FULFILLED,
        RELEASED
    }

    /**
     * @param competingCommitment human-readable - "CT-MAIN-000123, routine clean of Hall A 09:00-11:00 by
     *        Spotless Ltd" - so S173 can show it to the coordinator verbatim
     */
    record Reservation(UUID reservationId, Status status, UUID cleaningTaskId, UUID competingCommitmentId,
            String competingCommitment, Instant competingFrom, Instant competingTo) {
    }
}
