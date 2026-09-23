package gh.edu.clet.sfl.safetysecurity.lifesafety.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.safetysecurity.lifesafety.application.port.LifeSafetyRepository;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.event.LifeSafetyEventType;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.LifeSafetyEvent;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.LifeSafetyEventKind;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.RecordMetadata;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.lifesafety.infrastructure.integration.LifeSafetyIntegrationInbox;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.AuditPort;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.IntegrationEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SRS-SFL-S162a-01: authenticated, observe-only ingestion of life-safety events. SFL never issues a
 * command to the certified system here or anywhere else in this module (§0E) - it authenticates,
 * normalises, stores and surfaces, then hands a fire/panic event to {@link FastLaneTriggerService}.
 */
@Service
public class LifeSafetyEventIngestionService {

    private final LifeSafetyIntegrationInbox inbox;
    private final LifeSafetyRepository repository;
    private final LifeSafetyAccessPolicy access;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final FastLaneTriggerService fastLane;
    private final MusterService muster;
    private final Clock clock;

    public LifeSafetyEventIngestionService(LifeSafetyIntegrationInbox inbox, LifeSafetyRepository repository,
            LifeSafetyAccessPolicy access, AuditPort audit, IntegrationEventPublisher events,
            FastLaneTriggerService fastLane, MusterService muster, Clock clock) {
        this.inbox = inbox;
        this.repository = repository;
        this.access = access;
        this.audit = audit;
        this.events = events;
        this.fastLane = fastLane;
        this.muster = muster;
        this.clock = clock;
    }

    public record ObserveEvent(String source, String idempotencyKey, Instant signedAt, String signature,
            String rawPayload, String siteCode, String deviceId, String zoneCode, LifeSafetyEventKind kind,
            String externalEventId, ActorContext actor) {
    }

    @Transactional
    public LifeSafetyEvent observe(ObserveEvent command) {
        inbox.accept(new LifeSafetyIntegrationInbox.InboundMessage(command.source(), command.idempotencyKey(),
                "lifesafety.event.observed", command.siteCode(), command.signedAt(), command.signature(),
                command.rawPayload(), Map.of("kind", command.kind() == null ? "" : command.kind().name(),
                        "zoneCode", command.zoneCode() == null ? "" : command.zoneCode()),
                List.of("kind", "zoneCode"), command.actor()));

        var now = clock.instant();
        var event = new LifeSafetyEvent(UUID.randomUUID(), command.siteCode(), command.source(),
                command.externalEventId(), command.deviceId(), command.zoneCode(), command.kind(), now,
                RecordMetadata.createdBy(command.actor().actorId(), now, SourceChannel.INTEGRATION,
                        command.actor().correlationId()));
        var saved = repository.saveEvent(event);

        audit.record(command.actor(), SourceChannel.INTEGRATION.name(), saved.siteCode(), "LIFESAFETY_EVENT_OBSERVED",
                "LifeSafetyEvent", saved.id().toString(), null, saved, null);
        events.publish(LifeSafetyEventType.LIFESAFETY_EVENT_OBSERVED.eventType(),
                LifeSafetyEventType.LIFESAFETY_EVENT_OBSERVED.version(), "LifeSafetyEvent", saved.id().toString(),
                saved.siteCode(), command.actor(), Map.of("eventId", saved.id().toString(), "kind", saved.kind(),
                        "zoneCode", saved.zoneCode() == null ? "" : saved.zoneCode()));

        if (saved.isFireOrPanic()) {
            fastLane.trigger(saved, command.actor());
            muster.openForEvent(saved, command.actor());
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<LifeSafetyEvent> list(String siteCode, int limit, ActorContext actor) {
        access.require(actor, SflPermission.LIFESAFETY_EVENT_READ, siteCode, "LifeSafetyEvent", null);
        return repository.findEvents(siteCode, limit);
    }
}
