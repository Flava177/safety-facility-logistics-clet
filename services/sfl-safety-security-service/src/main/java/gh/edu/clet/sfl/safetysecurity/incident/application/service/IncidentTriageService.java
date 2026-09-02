package gh.edu.clet.sfl.safetysecurity.incident.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.safetysecurity.incident.application.port.SecurityIncidentRepository;
import gh.edu.clet.sfl.safetysecurity.incident.domain.event.IncidentEventType;
import gh.edu.clet.sfl.safetysecurity.incident.domain.exception.IncidentException;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.RiskRating;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.SecurityIncident;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.Severity;
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
 * SRS §D.9 step 2: severity and likelihood x impact risk rating, revisable during investigation.
 *
 * <p>Publishes {@code SECURITY_INCIDENT_EMERGENCY_ESCALATED} the moment {@link
 * SecurityIncident#emergencyEscalated()} first flips true - once, not on every subsequent re-triage,
 * since the escalation itself already happened and hard rule 2 only asks that it happen automatically
 * when it first does. A human command role (or, later, an S174 integration adapter) is what actually
 * reacts to the event; this service does not call into the emergency module directly - modules stay
 * decoupled per the architecture rule against cross-module persistence reach.
 */
@Service
public class IncidentTriageService {

    private final SecurityIncidentRepository repository;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final IncidentAccessPolicy access;
    private final Clock clock;

    public IncidentTriageService(SecurityIncidentRepository repository, AuditPort audit,
            IntegrationEventPublisher events, IncidentAccessPolicy access, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.events = events;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public SecurityIncident triage(Triage command) {
        ActorContext actor = command.actor();
        SecurityIncident incident = repository.findIncident(command.incidentId())
                .orElseThrow(() -> IncidentException.notFound("SecurityIncident", command.incidentId()));
        access.require(actor, SflPermission.INCIDENT_TRIAGE, incident.siteCode(), "SecurityIncident",
                incident.id().toString());
        incident.metadata().requireVersion(command.expectedVersion());
        boolean wasEscalated = incident.emergencyEscalated();

        Instant at = clock.instant();
        SecurityIncident triaged = incident.triage(command.severity(), command.riskRating(), command.reportable(),
                command.reportabilityNotes(), actor.actorId(), at, command.sourceChannel(), actor.correlationId());
        SecurityIncident saved = repository.saveIncident(triaged);

        audit.record(actor, command.sourceChannel().name(), saved.siteCode(), "SECURITY_INCIDENT_TRIAGED",
                "SecurityIncident", saved.id().toString(), incident, saved, null);
        events.publish(IncidentEventType.SECURITY_INCIDENT_TRIAGED.eventType(),
                IncidentEventType.SECURITY_INCIDENT_TRIAGED.version(), "SecurityIncident", saved.id().toString(),
                saved.siteCode(), actor, Map.of("incidentId", saved.id().toString(), "severity",
                        saved.severity().name()));

        if (!wasEscalated && saved.emergencyEscalated()) {
            audit.record(actor, command.sourceChannel().name(), saved.siteCode(),
                    "SECURITY_INCIDENT_EMERGENCY_ESCALATED", "SecurityIncident", saved.id().toString(), null, saved,
                    "Emergency-rated at triage - SRS-SFL-S163 hard rule 2");
            events.publish(IncidentEventType.SECURITY_INCIDENT_EMERGENCY_ESCALATED.eventType(),
                    IncidentEventType.SECURITY_INCIDENT_EMERGENCY_ESCALATED.version(), "SecurityIncident",
                    saved.id().toString(), saved.siteCode(), actor,
                    Map.of("incidentId", saved.id().toString(), "siteCode", saved.siteCode()));
        }
        return saved;
    }

    public record Triage(UUID incidentId, Severity severity, RiskRating riskRating, boolean reportable,
            String reportabilityNotes, Long expectedVersion, ActorContext actor, SourceChannel sourceChannel) {
    }
}
