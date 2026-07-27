package gh.edu.clet.sfl.emergencynotification.application.port;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.emergencynotification.domain.model.ChannelType;
import java.util.UUID;

/**
 * Provider-neutral outbound gateway for one channel of an activation. Vendor SDKs/types live only in
 * adapters; the Phase-1 recorded adapter never fakes vendor success — it reports what it actually did and
 * whether it fell back to a degraded/direct path.
 */
public interface NotificationGatewayPort {

    GatewaySendResult send(UUID activationId, ChannelType channel, String siteCode, int targetCount,
            boolean degradedMode, ActorContext actor);

    record GatewaySendResult(String provider, int accepted, boolean degraded) {
    }
}
