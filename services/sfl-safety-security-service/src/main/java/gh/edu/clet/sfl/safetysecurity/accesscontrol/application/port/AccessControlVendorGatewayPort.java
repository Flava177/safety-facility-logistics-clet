package gh.edu.clet.sfl.safetysecurity.accesscontrol.application.port;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessOverride;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessZone;
import java.util.UUID;

/**
 * Pushes zone/schedule/door-group definitions and overrides out to the purchased access-control
 * system - S160a's Buy-and-Integrate half (SFL owns the definition, the vendor system enforces it at
 * the door). No vendor is chosen yet; see {@code RecordedAccessControlVendorGateway}, which mirrors
 * S160's {@code RecordedBadgeDeviceGateway}.
 */
public interface AccessControlVendorGatewayPort {

    SyncResult syncZone(AccessZone zone, ActorContext actor);

    SyncResult syncOverride(AccessOverride override, ActorContext actor);

    /** Tells the vendor system an override's window has ended and normal rules should resume. */
    SyncResult syncOverrideReversion(UUID overrideId, ActorContext actor);

    record SyncResult(String provider, boolean synced) {
    }
}
