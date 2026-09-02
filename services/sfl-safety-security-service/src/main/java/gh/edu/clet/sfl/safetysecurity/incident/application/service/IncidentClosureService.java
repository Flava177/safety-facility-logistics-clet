package gh.edu.clet.sfl.safetysecurity.incident.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.safetysecurity.incident.application.port.SecurityIncidentRepository;
import gh.edu.clet.sfl.safetysecurity.incident.domain.event.IncidentEventType;
import gh.edu.clet.sfl.safetysecurity.incident.domain.exception.IncidentException;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.SecurityIncident;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.AuditPort;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.IntegrationEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SRS §D.9 hard rule 1: closes a case only once no mandatory corrective action remains open. The
 * count is read fresh from {@link SecurityIncidentRepository#countOpenMandatoryCorrectiveActions} in
 * the same transaction as the close, so a CAPA opened a moment ago cannot be missed.
 */
@Service
public class IncidentClosureService {

    private final SecurityIncidentRepository repository;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final IncidentAccessPolicy access;
    private final Clock clock;

    public IncidentClosureService(SecurityIncidentRepository repository, AuditPort audit,
            IntegrationEventPublisher events, IncidentAccessPolicy access, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.events = events;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public SecurityIncident close(Close command) {
        ActorContext actor = command.actor();
        SecurityIncident incident = repository.findIncident(command.incidentId())
                .orElseThrow(() -> IncidentException.notFound("SecurityIncident", command.incidentId()));
        access.require(actor, SflPermission.INCIDENT_CLOSE, incident.siteCode(), "SecurityIncident",
                incident.id().toString());
        incident.metadata().requireVersion(command.expectedVersion());

        long openMandatory = repository.countOpenMandatoryCorrectiveActions(incident.id());
        Instant at = clock.instant();
        SecurityIncident closed = incident.close(command.closureNotes(), openMandatory, actor.actorId(), at,
                command.sourceChannel(), actor.correlationId());
        SecurityIncident saved = repository.saveIncident(closed);

        audit.record(actor, command.sourceChannel().name(), saved.siteCode(), "SECURITY_INCIDENT_CLOSED",
                "SecurityIncident", saved.id().toString(), incident, saved, command.closureNotes());
        events.publish(IncidentEventType.SECURITY_INCIDENT_CLOSED.eventType(),
                IncidentEventType.SECURITY_INCIDENT_CLOSED.version(), "SecurityIncident", saved.id().toString(),
                saved.siteCode(), actor, Map.of("incidentId", saved.id().toString()));
        return saved;
    }

    public record Close(UUID incidentId, String closureNotes, Long expectedVersion, ActorContext actor,
            SourceChannel sourceChannel) {
    }
}
