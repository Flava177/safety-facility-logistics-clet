package gh.edu.clet.sfl.fleetlogistics.fleet.application.command;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleCategory;
import java.util.Set;
import java.util.UUID;

/**
 * Updates the descriptive attributes of a vehicle (SRS-SFL-S166-01).
 *
 * <p>{@code expectedVersion} carries the optimistic-lock version the client last read; a mismatch
 * raises the stable version-conflict error rather than silently overwriting a concurrent edit.
 * Registration number and site are deliberately not updatable here: changing either changes the
 * record's identity or its scope, which needs its own authorised transfer workflow.
 */
public record UpdateVehicleCommand(
        UUID vehicleId,
        String vin,
        String make,
        String model,
        int manufactureYear,
        VehicleCategory category,
        int capacity,
        String responsibleUnit,
        String operationalOwner,
        String acquisitionReference,
        boolean emergencyOnly,
        Set<OperatingMode> allowedOperatingModes,
        Long expectedVersion,
        ActorContext actor,
        SourceChannel sourceChannel) implements FleetCommand {
}
