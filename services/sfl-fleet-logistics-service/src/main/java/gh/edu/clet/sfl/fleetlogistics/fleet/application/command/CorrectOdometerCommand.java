package gh.edu.clet.sfl.fleetlogistics.fleet.application.command;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.util.UUID;

/**
 * The authorised odometer-correction workflow — the only route by which a reading may move backwards
 * (SRS-SFL-S166-01 record integrity, SRS-SFL-S166-03 before/after audit).
 *
 * <p>Both a reason and an evidence reference are mandatory, and the caller must hold
 * {@code FLEET_VEHICLE_ODOMETER_CORRECT}.
 */
public record CorrectOdometerCommand(
        UUID vehicleId,
        long correctedReading,
        String reason,
        UUID evidenceId,
        Long expectedVersion,
        ActorContext actor,
        SourceChannel sourceChannel) implements FleetCommand {
}
