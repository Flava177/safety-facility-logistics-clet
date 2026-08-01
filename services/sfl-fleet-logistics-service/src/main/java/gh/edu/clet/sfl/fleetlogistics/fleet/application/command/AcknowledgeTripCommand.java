package gh.edu.clet.sfl.fleetlogistics.fleet.application.command;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.TripAcknowledgementState;
import java.util.UUID;

/**
 * The assigned driver confirms or defers a trip (SRS-SFL-S166-02).
 *
 * <p>{@code answer} is the target state and is typed as the domain enum rather than a string or a
 * boolean {@code confirmed} flag. A boolean would have to grow a third value the first time anything
 * other than yes/no is needed, and a string would push the "is this a real answer" check out to
 * whichever caller remembered it.
 *
 * <p>{@code reason} is required when deferring and ignored when confirming — enforced by
 * {@code TripAcknowledgement}, not here, so the rule holds for every path into the aggregate.
 */
public record AcknowledgeTripCommand(
        UUID tripId,
        TripAcknowledgementState answer,
        String reason,
        Long expectedVersion,
        ActorContext actor,
        SourceChannel sourceChannel) implements FleetCommand {
}
