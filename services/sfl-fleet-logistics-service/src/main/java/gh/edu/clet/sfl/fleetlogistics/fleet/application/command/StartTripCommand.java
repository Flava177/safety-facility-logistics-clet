package gh.edu.clet.sfl.fleetlogistics.fleet.application.command;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.util.UUID;

/** Starts an assigned trip. Gated on a valid pre-trip inspection (SRS-SFL-S166-02). */
public record StartTripCommand(
        UUID tripId,
        long startOdometer,
        Long expectedVersion,
        ActorContext actor,
        SourceChannel sourceChannel) implements FleetCommand {
}
