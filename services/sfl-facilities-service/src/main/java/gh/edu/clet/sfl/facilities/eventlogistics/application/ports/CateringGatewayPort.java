package gh.edu.clet.sfl.facilities.eventlogistics.application.ports;

import java.time.Instant;
import java.util.Optional;

/**
 * Catering through S172 - SRS-SFL-S173-02: "the catering system (S172, Phase 3) where catering is in
 * scope and available".
 *
 * <p>S172 is Phase 3 and does not exist. The port exists anyway so that the day it does, catering is
 * one adapter and one configuration flag ({@code event-logistics.owning-system.S172.available}) away,
 * not a redesign. The shipped adapter answers empty - "there is no system to ask" - and S173 records a
 * manual coordination item.
 */
public interface CateringGatewayPort {

    /** Empty when no catering system is installed in this deployment. */
    Optional<GatewayOutcome> request(CateringRequest request);

    record CateringRequest(String siteCode, String locationCode, Instant from, Instant to, int covers,
            String description, String originReference, String requestedBy) {
    }
}
