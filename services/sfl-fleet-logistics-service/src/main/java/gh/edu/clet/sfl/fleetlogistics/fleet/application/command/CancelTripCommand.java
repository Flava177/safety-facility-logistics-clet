package gh.edu.clet.sfl.fleetlogistics.fleet.application.command;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.util.UUID;

/** Cancels a trip. Privileged, and the reason is mandatory (SRS-SFL-S166-02). */
public record CancelTripCommand(
        UUID tripId,
        String reason,
        Long expectedVersion,
        ActorContext actor,
        SourceChannel sourceChannel) implements FleetCommand {
}
