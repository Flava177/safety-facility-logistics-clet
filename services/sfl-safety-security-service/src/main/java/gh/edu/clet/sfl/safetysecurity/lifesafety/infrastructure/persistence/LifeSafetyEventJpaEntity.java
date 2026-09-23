package gh.edu.clet.sfl.safetysecurity.lifesafety.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.LifeSafetyEvent;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.LifeSafetyEventKind;
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

/** JPA mapping for {@link LifeSafetyEvent}. Immutable once ingested - no {@code @Version} needed. */
@Entity
@Table(name = "lifesafety_events", schema = "safety_security")
public class LifeSafetyEventJpaEntity {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;
    @Column(name = "source_system", nullable = false, length = 80)
    private String sourceSystem;
    @Column(name = "external_event_id", length = 200)
    private String externalEventId;
    @Column(name = "device_id", length = 120)
    private String deviceId;
    @Column(name = "zone_code", length = 80)
    private String zoneCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LifeSafetyEventKind kind;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(name = "created_by", nullable = false, length = 160)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "source_channel", nullable = false, length = 20)
    private SourceChannel sourceChannel;
    @Column(name = "correlation_id", length = 120)
    private String correlationId;

    protected LifeSafetyEventJpaEntity() {
    }

    public static LifeSafetyEventJpaEntity from(LifeSafetyEvent event) {
        LifeSafetyEventJpaEntity entity = new LifeSafetyEventJpaEntity();
        entity.id = event.id();
        entity.siteCode = event.siteCode();
        entity.sourceSystem = event.sourceSystem();
        entity.externalEventId = event.externalEventId();
        entity.deviceId = event.deviceId();
        entity.zoneCode = event.zoneCode();
        entity.kind = event.kind();
        entity.occurredAt = event.occurredAt();
        entity.createdBy = event.metadata().createdBy();
        entity.createdAt = event.metadata().createdAt();
        entity.sourceChannel = event.metadata().sourceChannel();
        entity.correlationId = event.metadata().correlationId();
        return entity;
    }

    public LifeSafetyEvent toDomain() {
        RecordMetadata metadata = RecordMetadata.rehydrate(createdBy, createdAt, createdBy, createdAt, 0L,
                sourceChannel, correlationId);
        return new LifeSafetyEvent(id, siteCode, sourceSystem, externalEventId, deviceId, zoneCode, kind, occurredAt,
                metadata);
    }
}
