package gh.edu.clet.sfl.safetysecurity.lifesafety.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.safetysecurity.lifesafety.application.port.EmergencyFastLanePort;
import gh.edu.clet.sfl.safetysecurity.lifesafety.application.port.LifeSafetyRepository;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.event.LifeSafetyEventType;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.FastLaneStatus;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.FastLaneTrigger;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.LifeSafetyEvent;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.RecordMetadata;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.AuditPort;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.IntegrationEventPublisher;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SRS-SFL-S162a-02: fast-lane emergency trigger on a fire/panic event, bypassing routine approval.
 * Records the trigger and its millisecond timing regardless of outcome - a degraded fallback is a
 * recorded, auditable outcome per the SRS's own "Emergency Trigger Degraded" error state, not a
 * silent failure.
 */
@Service
public class FastLaneTriggerService {

    private final LifeSafetyRepository repository;
    private final LifeSafetyAccessPolicy access;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final EmergencyFastLanePort emergency;
    private final Clock clock;

    public FastLaneTriggerService(LifeSafetyRepository repository, LifeSafetyAccessPolicy access, AuditPort audit,
            IntegrationEventPublisher events, EmergencyFastLanePort emergency, Clock clock) {
        this.repository = repository;
        this.access = access;
        this.audit = audit;
        this.events = events;
        this.emergency = emergency;
        this.clock = clock;
    }

    @Transactional
    public FastLaneTrigger trigger(LifeSafetyEvent event, ActorContext actor) {
        long start = clock.millis();
        String description = "Life-safety fast-lane trigger: " + event.kind() + " at " + event.siteCode() + "/"
                + event.zoneCode();
        var activationId = emergency.triggerFastLane(event.siteCode(), event.zoneCode(), description, actor);
        long latency = clock.millis() - start;
        var now = clock.instant();

        var trigger = new FastLaneTrigger(UUID.randomUUID(), event.siteCode(), event.zoneCode(), event.id(),
                activationId.orElse(null), activationId.isPresent() ? FastLaneStatus.TRIGGERED
                        : FastLaneStatus.DEGRADED, latency,
                activationId.isPresent() ? null : "No break-glass-eligible template/scenario configured", now,
                RecordMetadata.createdBy(actor.actorId(), now, SourceChannel.SYSTEM, actor.correlationId()));
        var saved = repository.saveFastLaneTrigger(trigger);

        audit.record(actor, SourceChannel.SYSTEM.name(), saved.siteCode(), "LIFESAFETY_FAST_LANE_TRIGGERED",
                "FastLaneTrigger", saved.id().toString(), null, saved, null);
        events.publish(LifeSafetyEventType.LIFESAFETY_FAST_LANE_TRIGGERED.eventType(),
                LifeSafetyEventType.LIFESAFETY_FAST_LANE_TRIGGERED.version(), "FastLaneTrigger",
                saved.id().toString(), saved.siteCode(), actor, Map.of("triggerId", saved.id().toString(),
                        "status", saved.status(), "latencyMillis", saved.latencyMillis(),
                        "activationId", saved.activationId() == null ? "" : saved.activationId().toString()));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<FastLaneTrigger> list(String siteCode, int limit, ActorContext actor) {
        access.require(actor, SflPermission.LIFESAFETY_FASTLANE_READ, siteCode, "FastLaneTrigger", null);
        return repository.findFastLaneTriggers(siteCode, limit);
    }
}
