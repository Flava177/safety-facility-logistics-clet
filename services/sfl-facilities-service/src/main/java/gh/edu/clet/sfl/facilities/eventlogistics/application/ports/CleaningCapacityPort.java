package gh.edu.clet.sfl.facilities.eventlogistics.application.ports;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Event cleaning through S169 - SRS-SFL-S173-02 ("S169 for cleaning") and S169-04 ("a conflict ... names
 * the competing commitment, not just states that none is available").
 *
 * <p>S173's own port over S169's {@code EventCleaningCapacity} contract, so S173 depends on the contract
 * through exactly one adapter and its tests can use a double. S169 is being built in parallel; where
 * the S169 implementation is not present the adapter answers
 * {@link GatewayOutcome#unavailable unavailable}, and the request becomes a manual coordination item -
 * never a silent drop and never a false confirmation.
 */
public interface CleaningCapacityPort {

    GatewayOutcome reserve(CleaningSlot slot);

    Optional<GatewayOutcome> state(String reservationId);

    void release(String reservationId, String reason);

    record CleaningSlot(String siteCode, UUID roomId, String locationCode, Instant from, Instant to, String scope,
            String eventReference, String requestedBy) {
    }
}
