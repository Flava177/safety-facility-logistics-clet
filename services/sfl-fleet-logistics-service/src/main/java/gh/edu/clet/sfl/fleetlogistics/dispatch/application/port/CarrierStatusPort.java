package gh.edu.clet.sfl.fleetlogistics.dispatch.application.port;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.time.Instant;
import java.util.UUID;

/**
 * Provider-neutral seam for optional courier-carrier status updates. Vendor DTOs stay in adapters; this
 * port carries only neutral status facts that attach to the custody record. Recorded adapter in Phase 1.
 */
public interface CarrierStatusPort {

    void recordCarrierStatus(UUID dispatchId, String carrier, String status, Instant occurredAt,
            ActorContext actor, SourceChannel sourceChannel);
}
