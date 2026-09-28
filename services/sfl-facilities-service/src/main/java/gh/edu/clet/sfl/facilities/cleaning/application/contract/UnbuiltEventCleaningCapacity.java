package gh.edu.clet.sfl.facilities.cleaning.application.contract;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Build scaffolding until the S169 module lands; the S169 build deletes this file. Refuses rather than
 * reserving, so nothing built against it can appear to have resourced cleaning that does not exist.
 */
@Component
class UnbuiltEventCleaningCapacity implements EventCleaningCapacity {

    @Override
    public Reservation reserve(ReservationRequest request) {
        throw new IllegalStateException("S169 cleaning capacity is not built in this build.");
    }

    @Override
    public Optional<Reservation> find(UUID reservationId) {
        return Optional.empty();
    }

    @Override
    public void release(UUID reservationId, String reason) {
    }
}
