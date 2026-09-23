package gh.edu.clet.sfl.safetysecurity.intrusion.infrastructure.integration;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.safetysecurity.intrusion.application.port.IntrusionPanelGatewayPort;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.DisarmOverride;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Phase-1 recorded intrusion-panel sync. Records what it would have pushed to the panels and reports
 * it honestly as not synced - never fabricates vendor success. Mirrors
 * {@code accesscontrol.infrastructure.integration.RecordedAccessControlVendorGateway}; a real adapter
 * replaces this without any domain change.
 */
@Component
public class RecordedIntrusionPanelGateway implements IntrusionPanelGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(RecordedIntrusionPanelGateway.class);

    @Override
    public SyncResult requestDisarm(DisarmOverride override, ActorContext actor) {
        log.info("Recorded intrusion disarm request id={} zone={} site={} - no intrusion-panel vendor configured",
                override.id(), override.zoneCode(), override.siteCode());
        return new SyncResult("NONE-RECORDED", false);
    }

    @Override
    public SyncResult requestReArm(String siteCode, String zoneCode, ActorContext actor) {
        log.info("Recorded intrusion re-arm request site={} zone={} - no intrusion-panel vendor configured",
                siteCode, zoneCode);
        return new SyncResult("NONE-RECORDED", false);
    }
}
