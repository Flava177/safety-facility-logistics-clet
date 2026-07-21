package gh.edu.clet.sfl.fleetlogistics.fleet.application.command;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.util.UUID;

/** Holds or resumes a trip (SRS-SFL-S166-02 hold and resume). */
public record HoldTripCommand(
        UUID tripId,
        HoldAction action,
        String reason,
        Long expectedVersion,
        ActorContext actor,
        SourceChannel sourceChannel) implements FleetCommand {

    public enum HoldAction {
        HOLD,
        RESUME
    }
}
