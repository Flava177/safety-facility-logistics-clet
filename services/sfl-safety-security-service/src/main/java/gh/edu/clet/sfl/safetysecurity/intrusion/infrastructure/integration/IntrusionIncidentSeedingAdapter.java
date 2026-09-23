package gh.edu.clet.sfl.safetysecurity.intrusion.infrastructure.integration;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.safetysecurity.incident.application.service.IncidentReportingService;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.IncidentSource;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.intrusion.application.port.IntrusionIncidentSeedingPort;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Seeds a security incident (S163) from an acknowledged intrusion alarm - see
 * {@code IntrusionIncidentSeedingPort}'s javadoc for why this in-process call to
 * {@code IncidentReportingService} is the approved contract rather than a bare cross-module call: it
 * lives in {@code infrastructure.integration}, behind a port, exactly like every other module
 * boundary crossing in this codebase (see {@code accesscontrol.infrastructure.integration.IncidentSeedingAdapter}),
 * and it is the only class in S162 that imports anything from the {@code incident} package.
 *
 * <p>Seeds as a system actor holding {@code SOC_OPERATOR} rather than forwarding whoever acknowledged
 * the alarm, for the same reason the accesscontrol adapter does: "an intrusion alarm seeded this
 * incident" should read the same regardless of which operator was on shift.
 */
@Component
public class IntrusionIncidentSeedingAdapter implements IntrusionIncidentSeedingPort {

    private static final String SEEDING_ACTOR_ID = "system:intrusion-alarm-seeding";

    private final IncidentReportingService incidentReporting;

    public IntrusionIncidentSeedingAdapter(IncidentReportingService incidentReporting) {
        this.incidentReporting = incidentReporting;
    }

    @Override
    public UUID seed(String siteCode, String description, ActorContext actor) {
        ActorContext systemActor = new ActorContext(new SiteScopedPrincipal(SEEDING_ACTOR_ID,
                "Intrusion Alarm Seeding", Set.of(SflRole.SOC_OPERATOR), Set.of("*"), true),
                actor == null ? SEEDING_ACTOR_ID : actor.correlationId());
        var incident = incidentReporting.report(new IncidentReportingService.ReportIncident(siteCode,
                IncidentSource.INTRUSION_SEED, false, SEEDING_ACTOR_ID, null, description, false, systemActor,
                SourceChannel.SYSTEM));
        return incident.id();
    }
}
