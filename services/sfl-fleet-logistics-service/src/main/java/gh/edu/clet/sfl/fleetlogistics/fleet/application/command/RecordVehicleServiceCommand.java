package gh.edu.clet.sfl.fleetlogistics.fleet.application.command;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ServiceOutcome;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ServiceType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Records a completed service event and the next-due schedule it sets (SRS-SFL-S166-01).
 *
 * <p>The odometer reading taken at the service also advances the vehicle's odometer, which is why a
 * reading lower than the current one is rejected rather than quietly ignored.
 */
public record RecordVehicleServiceCommand(
        UUID vehicleId,
        ServiceType serviceType,
        LocalDate performedOn,
        long odometerAtService,
        LocalDate nextDueOn,
        Long nextDueOdometer,
        String providerReference,
        String workSummary,
        ServiceOutcome outcome,
        UUID evidenceId,
        ActorContext actor,
        SourceChannel sourceChannel,
        String idempotencyKey) implements FleetCommand {

    public Map<String, Object> idempotencyPayload() {
        return Map.of(
                "vehicleId", String.valueOf(vehicleId),
                "serviceType", String.valueOf(serviceType),
                "performedOn", String.valueOf(performedOn),
                "odometerAtService", odometerAtService,
                "outcome", String.valueOf(outcome));
    }
}
