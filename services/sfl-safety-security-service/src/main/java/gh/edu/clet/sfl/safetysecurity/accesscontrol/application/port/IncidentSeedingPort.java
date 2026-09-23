package gh.edu.clet.sfl.safetysecurity.accesscontrol.application.port;

import gh.edu.clet.sfl.common.security.ActorContext;
import java.util.UUID;

/**
 * Seeds a security incident (S163) from an access exception - SRS-SFL-S160a-04 / SRS §11.6
 * ("Incident ... seeded from security/life-safety events"). A port rather than a direct call to
 * {@code IncidentReportingService.report(...)}: the classification doc's implementation rule
 * ("direct module-to-module business calls should be avoided except through approved contracts")
 * treats this cross-module seed as exactly such an approved contract - see {@code IncidentSeedingAdapter}.
 * Also publishes {@code AccessException} as an integration event on the shared outbox, so a future
 * drainer can seed the incident instead once one exists, without this port needing to change.
 */
public interface IncidentSeedingPort {

    UUID seed(String siteCode, String description, ActorContext actor);
}
