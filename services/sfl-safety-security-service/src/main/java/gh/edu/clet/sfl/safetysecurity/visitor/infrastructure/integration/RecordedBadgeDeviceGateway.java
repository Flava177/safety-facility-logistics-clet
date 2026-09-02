package gh.edu.clet.sfl.safetysecurity.visitor.infrastructure.integration;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.safetysecurity.visitor.application.port.BadgeDeviceGatewayPort;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Phase-1 recorded badge/access-control sync. Provider-neutral: it records what it would have sent
 * to the badge printer, kiosk or access-control system and reports it honestly as not synced - it
 * never fabricates vendor success. A real adapter replaces this without any domain change; vendor
 * DTOs would live only inside such an adapter, matching S174's {@code RecordedNotificationGateway}.
 */
@Component
public class RecordedBadgeDeviceGateway implements BadgeDeviceGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(RecordedBadgeDeviceGateway.class);

    @Override
    public BadgeSyncResult syncAssignment(UUID visitId, String badgeNumber, List<String> accessZones,
            String siteCode, ActorContext actor) {
        log.info("Recorded badge assignment sync visit={} badge={} zones={} site={} - no badge/access-control "
                + "provider configured", visitId, badgeNumber, accessZones, siteCode);
        return new BadgeSyncResult("NONE-RECORDED", false);
    }
}
