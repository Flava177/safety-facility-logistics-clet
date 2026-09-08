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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * SRS §D.9 hard rule 1: closes a case only once no mandatory corrective action remains open. The
 * count is read fresh from {@link SecurityIncidentRepository#countOpenMandatoryCorrectiveActions} in
 * the same transaction as the close, so a CAPA opened and committed before this transaction started
 * cannot be missed.
 *
 * <h2>Why {@code SERIALIZABLE}, not just the row lock on the count query</h2>
 *
 * <p>A CAPA opened by a transaction that is still running when this one starts is a different problem
 * than a stale read: it is a phantom insert, and {@code PESSIMISTIC_READ} (see {@code
 * CorrectiveActionJpaRepository#lockOpenMandatoryForClosure}) only locks rows a query actually
 * returns - it cannot lock the absence of a row that does not exist yet. Closing that gap needs
 * predicate-level conflict detection, which is what {@code SERIALIZABLE} gives Postgres: this
 * transaction's read of "open mandatory CAPAs for this incident" and {@link
 * CorrectiveActionService#open}'s read of "this incident" form a two-edge rw-dependency cycle when
 * both run concurrently and one is closing while the other is opening a mandatory action - Postgres
 * detects that cycle and aborts one side with a serialization failure rather than letting both commit.
 * {@link CorrectiveActionService#open} must run at the same isolation level for this to hold; Postgres
 * only guarantees the anomaly-free property across a set of transactions that are all serializable.
 * See {@code IncidentClosureCapaGateConcurrencyEndToEndTest} for the proof.
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

    @Transactional(isolation = Isolation.SERIALIZABLE)
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
