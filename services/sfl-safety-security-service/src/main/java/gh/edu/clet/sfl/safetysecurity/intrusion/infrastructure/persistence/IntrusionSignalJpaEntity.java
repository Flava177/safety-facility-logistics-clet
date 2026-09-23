package gh.edu.clet.sfl.safetysecurity.intrusion.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.IntrusionSignal;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.RecordMetadata;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.SignalType;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.SourceChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "intrusion_signals", schema = "safety_security")
public class IntrusionSignalJpaEntity {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;
    @Column(nullable = false, length = 80)
    private String source;
    @Column(name = "external_event_id", nullable = false, length = 200)
    private String externalEventId;
    @Column(name = "panel_id", nullable = false, length = 120)
    private String panelId;
    @Column(name = "zone_code", nullable = false, length = 80)
    private String zoneCode;
    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", nullable = false, length = 20)
    private SignalType signalType;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(name = "created_by", nullable = false, length = 160)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "last_modified_by", nullable = false, length = 160)
    private String lastModifiedBy;
    @Column(name = "last_modified_at", nullable = false)
    private Instant lastModifiedAt;
    @Version
    @Column(name = "record_version", nullable = false)
    private long recordVersion;
    @Enumerated(EnumType.STRING)
    @Column(name = "source_channel", nullable = false, length = 20)
    private SourceChannel sourceChannel;
    @Column(name = "correlation_id", length = 120)
    private String correlationId;

    protected IntrusionSignalJpaEntity() {
    }

    static IntrusionSignalJpaEntity from(IntrusionSignal signal) {
        IntrusionSignalJpaEntity entity = new IntrusionSignalJpaEntity();
        entity.id = signal.id();
        entity.siteCode = signal.siteCode();
        entity.source = signal.source();
        entity.externalEventId = signal.externalEventId();
        entity.panelId = signal.panelId();
        entity.zoneCode = signal.zoneCode();
        entity.signalType = signal.signalType();
        entity.occurredAt = signal.occurredAt();
        RecordMetadata metadata = signal.metadata();
        entity.createdBy = metadata.createdBy();
        entity.createdAt = metadata.createdAt();
        entity.lastModifiedBy = metadata.lastModifiedBy();
        entity.lastModifiedAt = metadata.lastModifiedAt();
        entity.sourceChannel = metadata.sourceChannel();
        entity.correlationId = metadata.correlationId();
        return entity;
    }

    IntrusionSignal toDomain() {
        RecordMetadata metadata = RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt,
                recordVersion, sourceChannel, correlationId);
        return new IntrusionSignal(id, siteCode, source, externalEventId, panelId, zoneCode, signalType, occurredAt,
                metadata);
    }
}
