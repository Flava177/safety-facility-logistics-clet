package gh.edu.clet.sfl.safetysecurity.intrusion.application.port;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.DisarmOverride;

/**
 * Pushes disarm/re-arm instructions out to the purchased intrusion panels - S162's Buy-and-Integrate
 * half (SFL owns the schedule and the authorisation, the panel performs the actual arming). No vendor
 * is chosen yet; see {@code RecordedIntrusionPanelGateway}, which mirrors
 * {@code accesscontrol.infrastructure.integration.RecordedAccessControlVendorGateway}.
 */
public interface IntrusionPanelGatewayPort {

    SyncResult requestDisarm(DisarmOverride override, ActorContext actor);

    /** Tells the panel a disarm's window has ended and normal (armed) rules should resume. */
    SyncResult requestReArm(String siteCode, String zoneCode, ActorContext actor);

    record SyncResult(String provider, boolean synced) {
    }
}
