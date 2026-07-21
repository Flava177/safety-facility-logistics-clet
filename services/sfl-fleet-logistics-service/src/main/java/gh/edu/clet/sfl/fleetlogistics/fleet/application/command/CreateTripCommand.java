package gh.edu.clet.sfl.fleetlogistics.fleet.application.command;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Creates a trip, optionally assigning the vehicle and driver in the same call (SRS-SFL-S166-02).
 *
 * <p>Creating and assigning together is the common case and avoids a window where a trip exists with
 * no owner; leaving both null creates a planned trip to be assigned later.
 */
public record CreateTripCommand(
        UUID vehicleId,
        UUID driverId,
        String siteCode,
        String purpose,
        String origin,
        String destination,
        OperatingMode operatingMode,
        Instant plannedStart,
        Instant plannedEnd,
        ActorContext actor,
        SourceChannel sourceChannel,
        String idempotencyKey) implements FleetCommand {

    public Map<String, Object> idempotencyPayload() {
        return Map.of(
                "vehicleId", String.valueOf(vehicleId),
                "driverId", String.valueOf(driverId),
                "siteCode", String.valueOf(siteCode),
                "plannedStart", String.valueOf(plannedStart),
                "plannedEnd", String.valueOf(plannedEnd),
                "purpose", String.valueOf(purpose));
    }
}
