package gh.edu.clet.sfl.fleetlogistics.fleet.application.command;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleLifecycleStatus;
import java.util.UUID;

/**
 * Moves a vehicle through its lifecycle (SRS-SFL-S166-01 active/inactive/suspended/archived).
 *
 * <p>A reason is mandatory: the audit trail must say why a vehicle left service, and restoring an
 * archived record is exactly the "authorised restoration workflow" the SRS requires.
 */
public record ChangeVehicleLifecycleCommand(
        UUID vehicleId,
        VehicleLifecycleStatus targetStatus,
        String reason,
        Long expectedVersion,
        ActorContext actor,
        SourceChannel sourceChannel) implements FleetCommand {
}
