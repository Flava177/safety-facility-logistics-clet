package gh.edu.clet.sfl.safetysecurity.incident.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.safetysecurity.incident.application.port.SecurityIncidentRepository;
import gh.edu.clet.sfl.safetysecurity.incident.domain.event.IncidentEventType;
import gh.edu.clet.sfl.safetysecurity.incident.domain.exception.IncidentErrorCode;
import gh.edu.clet.sfl.safetysecurity.incident.domain.exception.IncidentException;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.CorrectiveAction;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.SecurityIncident;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.AuditPort;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.IntegrationEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SRS §D.9 step 4: CAPA items - owner, due date, status. Overdue actions are computed, not stored - see {@link CorrectiveAction#isOverdue}. */
@Service
public class CorrectiveActionService {

    private final SecurityIncidentRepository repository;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final IncidentAccessPolicy access;
    private final Clock clock;

    public CorrectiveActionService(SecurityIncidentRepository repository, AuditPort audit,
            IntegrationEventPublisher events, IncidentAccessPolicy access, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.events = events;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public CorrectiveAction open(OpenCorrectiveAction command) {
        ActorContext actor = command.actor();
        SecurityIncident incident = requireIncident(command.incidentId());
        access.require(actor, SflPermission.INCIDENT_CAPA_MANAGE, incident.siteCode(), "SecurityIncident",
                incident.id().toString());

        Instant at = clock.instant();
        CorrectiveAction action = CorrectiveAction.open(UUID.randomUUID(), incident, command.description(),
                command.ownerId(), command.dueDate(), command.mandatory(), actor.actorId(), at);
        CorrectiveAction saved = repository.saveCorrectiveAction(action);

        audit.record(actor, command.sourceChannel().name(), incident.siteCode(), "SECURITY_INCIDENT_CAPA_OPENED",
                "SecurityIncident", incident.id().toString(), null, saved, null);
        events.publish(IncidentEventType.SECURITY_INCIDENT_CAPA_OPENED.eventType(),
                IncidentEventType.SECURITY_INCIDENT_CAPA_OPENED.version(), "SecurityIncident",
                incident.id().toString(), incident.siteCode(), actor,
                Map.of("incidentId", incident.id().toString(), "correctiveActionId", saved.id().toString(),
                        "mandatory", saved.mandatory()));
        return saved;
    }

    @Transactional
    public CorrectiveAction startProgress(TransitionCorrectiveAction command) {
        return transition(command, SflPermission.INCIDENT_CAPA_MANAGE, CorrectiveAction::startProgress, null);
    }

    /** Effectiveness verification - who is entitled is open question Q-163-5; gated on {@code INCIDENT_CAPA_VERIFY}. */
    @Transactional
    public CorrectiveAction verify(TransitionCorrectiveAction command) {
        return transition(command, SflPermission.INCIDENT_CAPA_VERIFY,
                (a, actorId, at) -> a.verify(command.notes(), actorId, at),
                IncidentEventType.SECURITY_INCIDENT_CAPA_VERIFIED);
    }

    @Transactional
    public CorrectiveAction cancel(TransitionCorrectiveAction command) {
        return transition(command, SflPermission.INCIDENT_CAPA_MANAGE,
                (a, actorId, at) -> a.cancel(command.notes(), actorId, at), null);
    }

    private CorrectiveAction transition(TransitionCorrectiveAction command, SflPermission permission,
            CapaTransitionFn transitionFn, IncidentEventType eventType) {
        ActorContext actor = command.actor();
        CorrectiveAction action = repository.findCorrectiveAction(command.correctiveActionId())
                .orElseThrow(() -> new IncidentException(IncidentErrorCode.INCIDENT_CAPA_NOT_FOUND,
                        Map.of("correctiveActionId", command.correctiveActionId().toString())));
        access.require(actor, permission, action.siteCode(), "CorrectiveAction", action.id().toString());

        Instant at = clock.instant();
        CorrectiveAction moved = transitionFn.apply(action, actor.actorId(), at);
        CorrectiveAction saved = repository.saveCorrectiveAction(moved);

        audit.record(actor, command.sourceChannel().name(), saved.siteCode(), "SECURITY_INCIDENT_CAPA_" + saved.status(),
                "CorrectiveAction", saved.id().toString(), action, saved, command.notes());
        if (eventType != null) {
            events.publish(eventType.eventType(), eventType.version(), "SecurityIncident",
                    saved.incidentId().toString(), saved.siteCode(), actor,
                    Map.of("incidentId", saved.incidentId().toString(), "correctiveActionId", saved.id().toString()));
        }
        return saved;
    }

    private SecurityIncident requireIncident(UUID id) {
        return repository.findIncident(id).orElseThrow(() -> IncidentException.notFound("SecurityIncident", id));
    }

    @FunctionalInterface
    private interface CapaTransitionFn {
        CorrectiveAction apply(CorrectiveAction action, String actorId, Instant at);
    }

    public record OpenCorrectiveAction(UUID incidentId, String description, String ownerId, LocalDate dueDate,
            boolean mandatory, ActorContext actor, SourceChannel sourceChannel) {
    }

    public record TransitionCorrectiveAction(UUID correctiveActionId, String notes, ActorContext actor,
            SourceChannel sourceChannel) {
    }
}
