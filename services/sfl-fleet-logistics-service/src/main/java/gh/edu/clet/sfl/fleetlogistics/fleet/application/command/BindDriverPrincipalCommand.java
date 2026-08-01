package gh.edu.clet.sfl.fleetlogistics.fleet.application.command;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.util.UUID;

/**
 * Links a driver profile to the identity that signs in as it — or unlinks it, with a null subject.
 *
 * <p>Its own command rather than a field on the update path, because it is a different decision with a
 * different consequence. Correcting a licence class changes what the driver may drive; changing this
 * changes whose trip list a person sees. Gated on {@code FLEET_DRIVER_MANAGE} and audited as its own
 * action, so "who gave this person access to that driver's records" is answerable from the audit trail
 * rather than by diffing two versions of a profile.
 */
public record BindDriverPrincipalCommand(
        UUID driverId,
        String principalSubject,
        Long expectedVersion,
        ActorContext actor,
        SourceChannel sourceChannel) implements FleetCommand {
}
