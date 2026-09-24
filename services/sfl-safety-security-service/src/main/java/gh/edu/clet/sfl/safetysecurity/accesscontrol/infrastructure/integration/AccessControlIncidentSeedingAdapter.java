package gh.edu.clet.sfl.safetysecurity.accesscontrol.infrastructure.integration;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.application.port.AccessControlIncidentSeedingPort;
import gh.edu.clet.sfl.safetysecurity.incident.application.service.IncidentReportingService;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.IncidentSource;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.SourceChannel;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Seeds a security incident (S163) from an access exception - see {@code AccessControlIncidentSeedingPort}'s
 * javadoc for why this in-process call to {@code IncidentReportingService} is the approved contract
 * rather than a bare cross-module call: it lives in {@code infrastructure.integration}, behind a port,
 * exactly like every other module boundary crossing in this codebase, and it is the only class in
 * S160a that imports anything from the {@code incident} package.
 *
 * <p>Seeds as a system actor holding {@code SOC_OPERATOR} (which carries {@code INCIDENT_REPORT_CREATE}
 * - see {@code IncidentPermissionMatrix}) rather than forwarding whoever triggered the exception: the
 * triggering actor may be the HMAC-authenticated integration call itself, which carries no SFL roles at
 * all, and "an access exception seeded this incident" should read the same regardless of who or what
 * raised the underlying exception.
 */
@Component
public class AccessControlIncidentSeedingAdapter implements AccessControlIncidentSeedingPort {

    private static final String SEEDING_ACTOR_ID = "system:access-control-exception-seeding";

    private final IncidentReportingService incidentReporting;

    public AccessControlIncidentSeedingAdapter(IncidentReportingService incidentReporting) {
        this.incidentReporting = incidentReporting;
    }

    @Override
    public UUID seed(String siteCode, String description, ActorContext actor) {
        ActorContext systemActor = new ActorContext(new SiteScopedPrincipal(SEEDING_ACTOR_ID,
                "Access Control Exception Seeding", Set.of(SflRole.SOC_OPERATOR), Set.of("*"), true),
                actor == null ? SEEDING_ACTOR_ID : actor.correlationId());
        var incident = incidentReporting.report(new IncidentReportingService.ReportIncident(siteCode,
                IncidentSource.ACCESS_SEED, false, SEEDING_ACTOR_ID, null, description, false, systemActor,
                SourceChannel.SYSTEM));
        return incident.id();
    }
}
