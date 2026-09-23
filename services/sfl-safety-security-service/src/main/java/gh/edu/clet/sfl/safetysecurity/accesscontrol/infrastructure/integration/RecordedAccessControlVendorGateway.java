package gh.edu.clet.sfl.safetysecurity.accesscontrol.infrastructure.integration;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.application.port.AccessControlVendorGatewayPort;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessOverride;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessZone;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Phase-1 recorded access-control vendor sync. Records what it would have pushed to the door
 * controllers and reports it honestly as not synced - never fabricates vendor success. Mirrors S160's
 * {@code RecordedBadgeDeviceGateway}; a real adapter replaces this without any domain change.
 */
@Component
public class RecordedAccessControlVendorGateway implements AccessControlVendorGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(RecordedAccessControlVendorGateway.class);

    @Override
    public SyncResult syncZone(AccessZone zone, ActorContext actor) {
        log.info("Recorded access-zone sync zone={} site={} schedule={} - no access-control vendor configured",
                zone.zoneCode(), zone.siteCode(), zone.schedule());
        return new SyncResult("NONE-RECORDED", false);
    }

    @Override
    public SyncResult syncOverride(AccessOverride override, ActorContext actor) {
        log.info("Recorded access-override sync id={} scope={} site={} - no access-control vendor configured",
                override.id(), override.scopeRef(), override.siteCode());
        return new SyncResult("NONE-RECORDED", false);
    }

    @Override
    public SyncResult syncOverrideReversion(UUID overrideId, ActorContext actor) {
        log.info("Recorded access-override reversion sync id={} - no access-control vendor configured", overrideId);
        return new SyncResult("NONE-RECORDED", false);
    }
}
