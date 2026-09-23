package gh.edu.clet.sfl.safetysecurity.lifesafety.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.FastLaneStatus;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.FastLaneTrigger;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.RecordMetadata;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.SourceChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lifesafety_fast_lane_triggers", schema = "safety_security")
public class FastLaneTriggerJpaEntity {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;
    @Column(name = "zone_code", length = 80)
    private String zoneCode;
    @Column(name = "life_safety_event_id", nullable = false)
    private UUID lifeSafetyEventId;
    @Column(name = "activation_id")
    private UUID activationId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FastLaneStatus status;
    @Column(name = "latency_millis", nullable = false)
    private long latencyMillis;
    @Column(length = 500)
    private String note;
    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;
    @Column(name = "created_by", nullable = false, length = 160)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "source_channel", nullable = false, length = 20)
    private SourceChannel sourceChannel;
    @Column(name = "correlation_id", length = 120)
    private String correlationId;

    protected FastLaneTriggerJpaEntity() {
    }

    public static FastLaneTriggerJpaEntity from(FastLaneTrigger t) {
        FastLaneTriggerJpaEntity e = new FastLaneTriggerJpaEntity();
        e.id = t.id();
        e.siteCode = t.siteCode();
        e.zoneCode = t.zoneCode();
        e.lifeSafetyEventId = t.lifeSafetyEventId();
        e.activationId = t.activationId();
        e.status = t.status();
        e.latencyMillis = t.latencyMillis();
        e.note = t.note();
        e.triggeredAt = t.triggeredAt();
        e.createdBy = t.metadata().createdBy();
        e.createdAt = t.metadata().createdAt();
        e.sourceChannel = t.metadata().sourceChannel();
        e.correlationId = t.metadata().correlationId();
        return e;
    }

    public FastLaneTrigger toDomain() {
        RecordMetadata metadata = RecordMetadata.rehydrate(createdBy, createdAt, createdBy, createdAt, 0L,
                sourceChannel, correlationId);
        return new FastLaneTrigger(id, siteCode, zoneCode, lifeSafetyEventId, activationId, status, latencyMillis,
                note, triggeredAt, metadata);
    }
}
