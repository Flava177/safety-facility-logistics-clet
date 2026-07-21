package gh.edu.clet.sfl.fleetlogistics.fleet.application.command;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.util.UUID;

/**
 * Closes a trip.
 *
 * <p>SRS-SFL-S166-02: "A workflow cannot be closed without required evidence or closure reason." Both
 * are required, along with an end odometer that does not regress.
 */
public record CloseTripCommand(
        UUID tripId,
        String closureReason,
        UUID closureEvidenceId,
        long endOdometer,
        Long expectedVersion,
        ActorContext actor,
        SourceChannel sourceChannel) implements FleetCommand {
}
