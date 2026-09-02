package gh.edu.clet.sfl.safetysecurity.visitor.application.port;

import gh.edu.clet.sfl.common.security.ActorContext;
import java.util.List;
import java.util.UUID;

/**
 * Syncs a badge/access-zone assignment out to the badge printer, kiosk or access-control system -
 * S160's "Hybrid" half (SFL owns the workflow, an adapter syncs badge/access permissions and device
 * events). No vendor is chosen yet; see {@code RecordedBadgeDeviceGateway}.
 */
public interface BadgeDeviceGatewayPort {

    BadgeSyncResult syncAssignment(UUID visitId, String badgeNumber, List<String> accessZones, String siteCode,
            ActorContext actor);

    record BadgeSyncResult(String provider, boolean synced) {
    }
}
