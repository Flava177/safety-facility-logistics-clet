package gh.edu.clet.sfl.fleetlogistics.fleet.application.command;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverLifecycleStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.LicenceClass;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Updates a driver profile, optionally moving it through its lifecycle in the same call.
 *
 * <p>A lifecycle change is expressed here rather than on a separate endpoint because suspending a
 * driver almost always accompanies a licence or clearance correction, and splitting the two would
 * leave a window where the profile is inconsistent.
 */
public record UpdateDriverCommand(
        UUID driverId,
        String displayName,
        String licenceNumber,
        LicenceClass licenceClass,
        LocalDate licenceExpiresOn,
        LocalDate medicalClearanceExpiresOn,
        String responsibleUnit,
        DriverLifecycleStatus targetLifecycleStatus,
        String lifecycleReason,
        Long expectedVersion,
        ActorContext actor,
        SourceChannel sourceChannel) implements FleetCommand {
}
