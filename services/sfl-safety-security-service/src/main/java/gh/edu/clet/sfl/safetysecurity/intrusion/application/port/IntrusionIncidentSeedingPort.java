package gh.edu.clet.sfl.safetysecurity.intrusion.application.port;

import gh.edu.clet.sfl.common.security.ActorContext;
import java.util.UUID;

/**
 * Seeds a security incident (S163) from an acknowledged alarm - SRS-SFL-S162-03. A port rather than a
 * direct call to {@code IncidentReportingService.report(...)}, for the same reason
 * {@code accesscontrol.application.port.IncidentSeedingPort} gives: the classification doc's
 * implementation rule treats this cross-module seed as an approved contract, not a bare cross-module
 * call - see {@code IntrusionIncidentSeedingAdapter}.
 */
public interface IntrusionIncidentSeedingPort {

    UUID seed(String siteCode, String description, ActorContext actor);
}
