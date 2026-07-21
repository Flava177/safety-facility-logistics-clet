package gh.edu.clet.sfl.fleetlogistics.fleet.application.command;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.util.UUID;

/**
 * Assigns or reassigns the vehicle and driver on a trip (SRS-SFL-S166-02 assignment and reassignment).
 *
 * <p>A reason is required when this is a reassignment, because the transition history has to explain
 * why an assignment moved.
 */
public record AssignTripCommand(
        UUID tripId,
        UUID vehicleId,
        UUID driverId,
        String reason,
        Long expectedVersion,
        ActorContext actor,
        SourceChannel sourceChannel) implements FleetCommand {
}
