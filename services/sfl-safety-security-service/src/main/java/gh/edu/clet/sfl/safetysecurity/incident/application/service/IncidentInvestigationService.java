package gh.edu.clet.sfl.safetysecurity.incident.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.safetysecurity.incident.application.port.SecurityIncidentRepository;
import gh.edu.clet.sfl.safetysecurity.incident.domain.event.IncidentEventType;
import gh.edu.clet.sfl.safetysecurity.incident.domain.exception.IncidentException;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.IncidentEvidence;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.IncidentStatus;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.RetentionClass;
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
 * SRS §D.9 steps 3 and 5: opening (and updating) the investigation, and preserving evidence with
 * provenance and a hash. Grouped in one service because both act on an already-triaged incident
 * without touching its own status further (evidence attachment is not a transition at all).
 */
@Service
public class IncidentInvestigationService {

    private final SecurityIncidentRepository repository;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final IncidentAccessPolicy access;
    private final Clock clock;

    public IncidentInvestigationService(SecurityIncidentRepository repository, AuditPort audit,
            IntegrationEventPublisher events, IncidentAccessPolicy access, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.events = events;
        this.access = access;
        this.clock = clock;
    }

    /**
     * Opens the investigation if the incident is still in {@link IncidentStatus#TRIAGE}, or updates
     * the investigation notes in place if it is already {@link IncidentStatus#INVESTIGATING} - one
     * endpoint for both, since from the caller's point of view "record what the investigator knows
     * now" is the same act either way.
     */
    @Transactional
    public SecurityIncident openOrUpdateInvestigation(OpenOrUpdateInvestigation command) {
        ActorContext actor = command.actor();
        SecurityIncident incident = requireIncident(command.incidentId());
        access.require(actor, SflPermission.INCIDENT_INVESTIGATE, incident.siteCode(), "SecurityIncident",
                incident.id().toString());
        incident.metadata().requireVersion(command.expectedVersion());

        Instant at = clock.instant();
        boolean opening = incident.status() == IncidentStatus.TRIAGE;
        SecurityIncident updated = opening
                ? incident.openInvestigation(command.investigatorId(), command.investigationNotes(),
                        actor.actorId(), at, command.sourceChannel(), actor.correlationId())
                : incident.updateInvestigationNotes(command.investigationNotes(), actor.actorId(), at,
                        command.sourceChannel(), actor.correlationId());
        SecurityIncident saved = repository.saveIncident(updated);

        if (opening) {
            audit.record(actor, command.sourceChannel().name(), saved.siteCode(),
                    "SECURITY_INCIDENT_INVESTIGATION_OPENED", "SecurityIncident", saved.id().toString(), incident,
                    saved, null);
            events.publish(IncidentEventType.SECURITY_INCIDENT_INVESTIGATION_OPENED.eventType(),
                    IncidentEventType.SECURITY_INCIDENT_INVESTIGATION_OPENED.version(), "SecurityIncident",
                    saved.id().toString(), saved.siteCode(), actor,
                    Map.of("incidentId", saved.id().toString(), "investigatorId", saved.investigatorId()));
        } else {
            audit.record(actor, command.sourceChannel().name(), saved.siteCode(),
                    "SECURITY_INCIDENT_INVESTIGATION_UPDATED", "SecurityIncident", saved.id().toString(), incident,
                    saved, null);
        }
        return saved;
    }

    @Transactional
    public IncidentEvidence attachEvidence(AttachEvidence command) {
        ActorContext actor = command.actor();
        SecurityIncident incident = requireIncident(command.incidentId());
        access.require(actor, SflPermission.INCIDENT_EVIDENCE_MANAGE, incident.siteCode(), "SecurityIncident",
                incident.id().toString());

        Instant at = clock.instant();
        IncidentEvidence evidence = IncidentEvidence.attach(UUID.randomUUID(), incident, command.fileReference(),
                command.fileName(), command.mediaType(), command.sizeBytes(), command.contentHash(),
                command.retentionClass(), command.notes(), actor.actorId(), at);
        IncidentEvidence saved = repository.saveEvidence(evidence);

        audit.record(actor, command.sourceChannel().name(), incident.siteCode(), "SECURITY_INCIDENT_EVIDENCE_ATTACHED",
                "SecurityIncident", incident.id().toString(), null, saved, null);
        events.publish(IncidentEventType.SECURITY_INCIDENT_EVIDENCE_ATTACHED.eventType(),
                IncidentEventType.SECURITY_INCIDENT_EVIDENCE_ATTACHED.version(), "SecurityIncident",
                incident.id().toString(), incident.siteCode(), actor,
                Map.of("incidentId", incident.id().toString(), "evidenceId", saved.id().toString()));
        return saved;
    }

    private SecurityIncident requireIncident(UUID id) {
        return repository.findIncident(id).orElseThrow(() -> IncidentException.notFound("SecurityIncident", id));
    }

    public record OpenOrUpdateInvestigation(UUID incidentId, String investigatorId, String investigationNotes,
            Long expectedVersion, ActorContext actor, SourceChannel sourceChannel) {
    }

    public record AttachEvidence(UUID incidentId, String fileReference, String fileName, String mediaType,
            Long sizeBytes, String contentHash, RetentionClass retentionClass, String notes, ActorContext actor,
            SourceChannel sourceChannel) {
    }
}
