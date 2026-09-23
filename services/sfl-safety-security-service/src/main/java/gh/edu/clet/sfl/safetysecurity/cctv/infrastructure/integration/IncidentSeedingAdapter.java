package gh.edu.clet.sfl.safetysecurity.cctv.infrastructure.integration;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.safetysecurity.cctv.application.port.IncidentSeedingPort;
import gh.edu.clet.sfl.safetysecurity.incident.application.service.IncidentReportingService;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.IncidentSource;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.SourceChannel;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Seeds a security incident (S163) from a video-analytics alert - see {@code IncidentSeedingPort}'s
 * javadoc. Lives in {@code infrastructure.integration}, behind a port, exactly like S160a's own
 * adapter of the same name; this is the only class in S161 that imports anything from the
 * {@code incident} package, and it uses {@code IncidentSource.CCTV_SEED}, which that module already
 * reserved for exactly this producer.
 *
 * <p>Seeds as a system actor holding {@code SOC_OPERATOR} for the same reason S160a's adapter does:
 * the triggering actor is the HMAC-authenticated integration call, which carries no SFL roles.
 */
@Component
public class IncidentSeedingAdapter implements IncidentSeedingPort {

    private static final String SEEDING_ACTOR_ID = "system:cctv-analytics-alert-seeding";

    private final IncidentReportingService incidentReporting;

    public IncidentSeedingAdapter(IncidentReportingService incidentReporting) {
        this.incidentReporting = incidentReporting;
    }

    @Override
    public UUID seed(String siteCode, String description, ActorContext actor) {
        ActorContext systemActor = new ActorContext(new SiteScopedPrincipal(SEEDING_ACTOR_ID,
                "CCTV Analytics Alert Seeding", Set.of(SflRole.SOC_OPERATOR), Set.of("*"), true),
                actor == null ? SEEDING_ACTOR_ID : actor.correlationId());
        var incident = incidentReporting.report(new IncidentReportingService.ReportIncident(siteCode,
                IncidentSource.CCTV_SEED, false, SEEDING_ACTOR_ID, null, description, false, systemActor,
                SourceChannel.SYSTEM));
        return incident.id();
    }
}
