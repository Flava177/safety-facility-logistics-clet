package gh.edu.clet.sfl.safetysecurity.cctv.application.port;

import gh.edu.clet.sfl.common.security.ActorContext;
import java.util.UUID;

/**
 * Seeds a security incident (S163) from a video-analytics alert - SRS-SFL-S161-04 / SRS §11.6
 * ("Incident ... seeded from security/life-safety events"). A port rather than a direct call, exactly
 * like S160a's {@code CctvIncidentSeedingPort} - see {@code CctvIncidentSeedingAdapter}.
 */
public interface CctvIncidentSeedingPort {

    UUID seed(String siteCode, String description, ActorContext actor);
}
