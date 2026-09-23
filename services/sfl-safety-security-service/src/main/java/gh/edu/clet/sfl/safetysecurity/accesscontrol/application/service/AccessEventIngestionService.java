package gh.edu.clet.sfl.safetysecurity.accesscontrol.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.application.port.AccessControlRepository;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.event.AccessControlEventType;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessDirection;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessEvent;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessEventKind;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.ExceptionRuleCode;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.ExceptionSeverity;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.ReaderHealth;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.ReaderHealthStatus;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.infrastructure.integration.AccessControlIntegrationInbox;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.AuditPort;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.IntegrationEventPublisher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SRS-SFL-S160a-01/04/06: authenticated ingestion of vendor access events and reader health,
 * repeated-denial/forced-open/tailgating detection, and anti-passback detection derived from
 * entry/exit events.
 *
 * <h2>Deliberate scope cut: {@code OUT_OF_HOURS} and {@code RESTRICTED_ZONE}</h2>
 *
 * <p>Both rule codes exist and can be raised (including by a human, through {@code
 * AccessExceptionService.raise}), but this service does not evaluate them automatically: doing so
 * correctly needs a real schedule parser against {@link gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessZone#schedule()},
 * which is intentionally kept as plain text for Phase 1 (see that class's javadoc) rather than a
 * structured schedule DSL. Auto-detecting these two is a natural next slice once a schedule DSL exists,
 * not a gap in what ships here.
 */
@Service
public class AccessEventIngestionService {

    /** Three or more denials for the same person at the same zone, among the last ten events, reads as
     * a repeated-denial pattern rather than one mistaken badge tap. */
    private static final int REPEATED_DENIAL_THRESHOLD = 3;
    private static final int RECENT_EVENTS_WINDOW = 10;
    private static final Duration TAILGATING_WINDOW = Duration.ofSeconds(5);

    private final AccessControlRepository repository;
    private final AccessControlIntegrationInbox inbox;
    private final AccessExceptionService exceptions;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final Clock clock;

    public AccessEventIngestionService(AccessControlRepository repository, AccessControlIntegrationInbox inbox,
            AccessExceptionService exceptions, AuditPort audit, IntegrationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.inbox = inbox;
        this.exceptions = exceptions;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    public record IngestEvent(String source, String externalEventId, Instant signedAt, String signature,
            String rawPayload, Map<String, Object> payload, String siteCode, String readerId, String doorId,
            String zoneCode, String personRef, AccessEventKind kind, AccessDirection direction, Instant occurredAt,
            ActorContext actor) {
    }

    public record ReportHealth(String source, String externalEventId, Instant signedAt, String signature,
            String rawPayload, Map<String, Object> payload, String siteCode, String readerId, String zoneCode,
            ReaderHealthStatus status, Instant observedAt, ActorContext actor) {
    }

    @Transactional
    public AccessEvent ingest(IngestEvent command) {
        inbox.accept(new AccessControlIntegrationInbox.InboundMessage(command.source(), command.externalEventId(),
                AccessControlEventType.ACCESS_EVENT_INGESTED.eventType(), command.siteCode(), command.signedAt(),
                command.signature(), command.rawPayload(), command.payload(),
                List.of("readerId", "zoneCode", "kind"), command.actor()));

        Optional<AccessEvent> existing = repository.findEventByExternalId(command.source(),
                command.externalEventId());
        if (existing.isPresent()) {
            return existing.get();
        }

        ActorContext actor = command.actor();
        Instant now = clock.instant();

        // Captured BEFORE this event is saved - see detectAntiPassback, which must compare against
        // what was true immediately before this event, not against this event once it is its own answer.
        Optional<AccessEvent> priorGranted = command.kind() == AccessEventKind.GRANTED
                && command.direction() != AccessDirection.UNKNOWN && command.personRef() != null
                        ? repository.findLastGrantedEvent(command.siteCode(), command.zoneCode(), command.personRef())
                        : Optional.empty();

        AccessEvent event = AccessEvent.ingest(UUID.randomUUID(), command.siteCode(), command.source(),
                command.externalEventId(), command.readerId(), command.doorId(), command.zoneCode(),
                command.personRef(), command.kind(), command.direction(), command.occurredAt(), actor.actorId(), now,
                SourceChannel.INTEGRATION, actor.correlationId());
        AccessEvent saved = repository.saveEvent(event);

        audit.record(actor, SourceChannel.INTEGRATION.name(), saved.siteCode(), "ACCESS_EVENT_INGESTED",
                "AccessEvent", saved.id().toString(), null, saved, null);
        events.publish(AccessControlEventType.ACCESS_EVENT_INGESTED.eventType(),
                AccessControlEventType.ACCESS_EVENT_INGESTED.version(), "AccessEvent", saved.id().toString(),
                saved.siteCode(), actor, Map.of("kind", saved.kind().name(), "zoneCode", saved.zoneCode()));

        detectExceptions(saved, priorGranted, actor);
        return saved;
    }

    @Transactional
    public ReaderHealth reportHealth(ReportHealth command) {
        inbox.accept(new AccessControlIntegrationInbox.InboundMessage(command.source(), command.externalEventId(),
                AccessControlEventType.ACCESS_READER_HEALTH_CHANGED.eventType(), command.siteCode(),
                command.signedAt(), command.signature(), command.rawPayload(), command.payload(),
                List.of("readerId", "zoneCode", "status"), command.actor()));

        ActorContext actor = command.actor();
        Instant now = clock.instant();
        Optional<ReaderHealth> existing = repository.findReaderHealth(command.siteCode(), command.readerId());
        ReaderHealthStatus previousStatus = existing.map(ReaderHealth::status).orElse(null);
        ReaderHealth health = existing
                .map(h -> h.update(command.status(), command.observedAt(), actor.actorId(), now,
                        SourceChannel.INTEGRATION, actor.correlationId()))
                .orElseGet(() -> ReaderHealth.report(UUID.randomUUID(), command.siteCode(), command.readerId(),
                        command.zoneCode(), command.status(), command.observedAt(), actor.actorId(), now,
                        SourceChannel.INTEGRATION, actor.correlationId()));
        ReaderHealth saved = repository.saveReaderHealth(health);

        audit.record(actor, SourceChannel.INTEGRATION.name(), saved.siteCode(), "ACCESS_READER_HEALTH_CHANGED",
                "ReaderHealth", saved.id().toString(), existing.orElse(null), saved, null);
        events.publish(AccessControlEventType.ACCESS_READER_HEALTH_CHANGED.eventType(),
                AccessControlEventType.ACCESS_READER_HEALTH_CHANGED.version(), "ReaderHealth", saved.id().toString(),
                saved.siteCode(), actor, Map.of("readerId", saved.readerId(), "status", saved.status().name()));

        boolean justWentBad = (saved.status() == ReaderHealthStatus.OFFLINE
                || saved.status() == ReaderHealthStatus.TAMPERED) && previousStatus != saved.status();
        if (justWentBad) {
            exceptions.raise(saved.siteCode(), null, saved.readerId(), saved.zoneCode(),
                    ExceptionRuleCode.READER_OFFLINE, ExceptionSeverity.HIGH, actor);
        }
        return saved;
    }

    /** SRS-SFL-S160a-04 (forced-open, repeated denial, tailgating) and S160a-06 (anti-passback). */
    private void detectExceptions(AccessEvent event, Optional<AccessEvent> priorGranted, ActorContext actor) {
        if (event.kind() == AccessEventKind.FORCED_OPEN) {
            exceptions.raise(event.siteCode(), event.id(), event.readerId(), event.zoneCode(),
                    ExceptionRuleCode.FORCED_OPEN, ExceptionSeverity.CRITICAL, actor);
        }

        if (event.kind() == AccessEventKind.DENIED && event.personRef() != null) {
            List<AccessEvent> recent = repository.findRecentEvents(event.siteCode(), event.zoneCode(),
                    event.personRef(), RECENT_EVENTS_WINDOW);
            long consecutiveDenials = recent.stream().takeWhile(e -> e.kind() == AccessEventKind.DENIED).count();
            if (consecutiveDenials + 1 >= REPEATED_DENIAL_THRESHOLD) {
                exceptions.raise(event.siteCode(), event.id(), event.readerId(), event.zoneCode(),
                        ExceptionRuleCode.REPEATED_DENIAL, ExceptionSeverity.MEDIUM, actor);
            }
        }

        if (event.kind() == AccessEventKind.GRANTED && event.direction() != AccessDirection.UNKNOWN
                && event.personRef() != null) {
            detectTailgating(event, actor);
            detectAntiPassback(event, priorGranted, actor);
        }
    }

    private void detectTailgating(AccessEvent event, ActorContext actor) {
        List<AccessEvent> recent = repository.findRecentEvents(event.siteCode(), event.zoneCode(), null, 5);
        boolean tailgated = recent.stream()
                .filter(e -> e.readerId().equals(event.readerId()) && !e.id().equals(event.id())
                        && e.kind() == AccessEventKind.GRANTED && e.direction() == event.direction()
                        && !event.personRef().equals(e.personRef()))
                .anyMatch(e -> Duration.between(e.occurredAt(), event.occurredAt()).abs().compareTo(TAILGATING_WINDOW)
                        <= 0);
        if (tailgated) {
            exceptions.raise(event.siteCode(), event.id(), event.readerId(), event.zoneCode(),
                    ExceptionRuleCode.TAILGATING, ExceptionSeverity.HIGH, actor);
        }
    }

    private void detectAntiPassback(AccessEvent event, Optional<AccessEvent> priorGranted, ActorContext actor) {
        boolean violatesAntiPassback = priorGranted.isPresent()
                && priorGranted.get().direction() == event.direction();
        if (violatesAntiPassback) {
            exceptions.raise(event.siteCode(), event.id(), event.readerId(), event.zoneCode(),
                    ExceptionRuleCode.ANTI_PASSBACK, ExceptionSeverity.MEDIUM, actor);
        }
    }
}
