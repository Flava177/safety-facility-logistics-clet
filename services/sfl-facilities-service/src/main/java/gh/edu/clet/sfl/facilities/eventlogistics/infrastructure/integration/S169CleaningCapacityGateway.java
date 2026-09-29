package gh.edu.clet.sfl.facilities.eventlogistics.infrastructure.integration;

import gh.edu.clet.sfl.facilities.cleaning.application.contract.EventCleaningCapacity;
import gh.edu.clet.sfl.facilities.eventlogistics.application.ports.CleaningCapacityPort;
import gh.edu.clet.sfl.facilities.eventlogistics.application.ports.GatewayOutcome;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.ResourceRequestStatus;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Event cleaning through S169's {@link EventCleaningCapacity} contract - SRS-SFL-S173-02, S169-04.
 *
 * <p>The only S173 class that knows S169 exists, and it knows only the contract: S169 is built in
 * parallel and S173 must not depend on its internals. A CONFLICT carries S169's own naming of the
 * competing commitment, which S173 shows verbatim ("CT-MAIN-000123, routine clean of Hall A 09:00-11:00
 * by Spotless Ltd") rather than a bare refusal - S169-04's acceptance criterion, seen from this side.
 *
 * <h2>When S169 is not there</h2>
 *
 * <p>Until the S169 build lands, the contract's placeholder bean refuses with an
 * {@link IllegalStateException}. That is caught here - it is thrown by a plain bean, not across a
 * transactional proxy, so catching it does not poison the caller's transaction - and answered as
 * <em>unavailable</em>: the request becomes a manual coordination item with the reason on it. Never a
 * drop, never a confirmation of cleaning nobody scheduled.
 */
@Component
public class S169CleaningCapacityGateway implements CleaningCapacityPort {

    private static final Logger log = LoggerFactory.getLogger(S169CleaningCapacityGateway.class);

    private final EventCleaningCapacity capacity;

    public S169CleaningCapacityGateway(EventCleaningCapacity capacity) {
        this.capacity = capacity;
    }

    @Override
    public GatewayOutcome reserve(CleaningSlot slot) {
        EventCleaningCapacity.Reservation reservation;
        try {
            reservation = capacity.reserve(new EventCleaningCapacity.ReservationRequest(slot.siteCode(), slot.roomId(),
                    slot.locationCode(), slot.from(), slot.to(), slot.scope(), slot.eventReference(),
                    slot.requestedBy()));
        } catch (IllegalStateException notBuilt) {
            log.warn("S169 event cleaning capacity is not available in this build; recording request {} as manual"
                    + " coordination", slot.eventReference());
            return GatewayOutcome.unavailable("S169 Cleaning & Janitorial Schedule Management cannot take event"
                    + " reservations in this deployment (" + notBuilt.getMessage() + "). Arrange cleaning"
                    + " off-system and record who accepted it.");
        }
        return map(reservation);
    }

    @Override
    public Optional<GatewayOutcome> state(String reservationId) {
        return capacity.find(UUID.fromString(reservationId)).map(S169CleaningCapacityGateway::map);
    }

    @Override
    public void release(String reservationId, String reason) {
        capacity.release(UUID.fromString(reservationId), reason);
    }

    private static GatewayOutcome map(EventCleaningCapacity.Reservation reservation) {
        String reference = reservation.reservationId() == null ? null : reservation.reservationId().toString();
        return switch (reservation.status()) {
            case RESERVED -> GatewayOutcome.of(ResourceRequestStatus.CONFIRMED, reference,
                    "Reserved in S169" + (reservation.cleaningTaskId() == null ? "."
                            : "; cleaning task " + reservation.cleaningTaskId() + "."));
            case FULFILLED -> GatewayOutcome.of(ResourceRequestStatus.FULFILLED, reference, "Cleaning completed in S169.");
            case CONFLICT -> new GatewayOutcome(ResourceRequestStatus.CONFLICTED, null, null,
                    "S169 cannot take it: " + reservation.competingCommitment()
                            + (reservation.competingFrom() == null ? ""
                                    : " (" + reservation.competingFrom() + " to " + reservation.competingTo() + ")"),
                    reservation.competingCommitment());
            case RELEASED -> new GatewayOutcome(ResourceRequestStatus.CONFLICTED, reference, null,
                    "The S169 reservation was released.", "S169 reservation released");
        };
    }
}
