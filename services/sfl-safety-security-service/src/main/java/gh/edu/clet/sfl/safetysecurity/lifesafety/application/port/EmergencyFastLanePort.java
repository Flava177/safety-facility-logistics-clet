package gh.edu.clet.sfl.safetysecurity.lifesafety.application.port;

import gh.edu.clet.sfl.common.security.ActorContext;
import java.util.Optional;
import java.util.UUID;

/**
 * SRS-SFL-S162a-02: the fast-lane trigger into S174. S174 lives in the same deployable, so the real
 * adapter calls {@code ActivationService.breakGlass} in-process - this port exists so the lifesafety
 * domain/application layers do not import the emergency package directly (only the one adapter that
 * implements this port may - mirrors how S160a's {@code IncidentSeedingPort} crosses into {@code incident}).
 */
public interface EmergencyFastLanePort {

    /**
     * Attempts to break-glass-activate the emergency workflow for the given site/zone. Returns the
     * resulting activation id, or empty if no break-glass-eligible template/scenario is configured -
     * the caller records that as a degraded fast-lane trigger rather than failing the observation.
     */
    Optional<UUID> triggerFastLane(String siteCode, String zoneCode, String description, ActorContext actor);
}
