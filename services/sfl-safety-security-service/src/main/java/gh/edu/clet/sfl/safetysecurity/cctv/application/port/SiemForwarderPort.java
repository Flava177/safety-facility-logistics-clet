package gh.edu.clet.sfl.safetysecurity.cctv.application.port;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.AnalyticsAlert;

/**
 * Forwards a security-relevant video-analytics alert to the SIEM/SOC tooling - SRS-SFL-S161-04. No
 * SIEM is chosen yet; see {@code RecordedSiemForwarder}, the same honest-stub stance every other
 * not-yet-integrated vendor in this codebase takes. A separate copy from {@code accesscontrol}'s port
 * of the same name - each module owns its own integration surface even where the shape matches.
 */
public interface SiemForwarderPort {

    ForwardResult forward(AnalyticsAlert alert, ActorContext actor);

    record ForwardResult(String provider, boolean forwarded) {
    }
}
