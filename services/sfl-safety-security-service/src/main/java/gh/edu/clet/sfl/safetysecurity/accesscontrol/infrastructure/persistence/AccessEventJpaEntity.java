package gh.edu.clet.sfl.safetysecurity.accesscontrol.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessDirection;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessEvent;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessEventKind;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.RecordMetadata;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.SourceChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link AccessEvent} - append-only, so no {@code @Version} field (an ingested event
 * is never revised, only ever inserted once per {@code (source, externalEventId)}). */
@Entity
@Table(name = "access_events", schema = "safety_security")
public class AccessEventJpaEntity {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;
    @Column(name = "source_system", nullable = false, length = 80)
    private String source;
    @Column(name = "external_event_id", nullable = false, length = 200)
    private String externalEventId;
    @Column(name = "reader_id", nullable = false, length = 120)
    private String readerId;
    @Column(name = "door_id", length = 120)
    private String doorId;
    @Column(name = "zone_code", nullable = false, length = 80)
    private String zoneCode;
    @Column(name = "person_ref", length = 160)
    private String personRef;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccessEventKind kind;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccessDirection direction;
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
    @Column(name = "record_version", nullable = false)
    private long recordVersion;
    @Enumerated(EnumType.STRING)
    @Column(name = "source_channel", nullable = false, length = 20)
    private SourceChannel sourceChannel;
    @Column(name = "correlation_id", length = 120)
    private String correlationId;

    protected AccessEventJpaEntity() {
    }

    public static AccessEventJpaEntity from(AccessEvent event) {
        AccessEventJpaEntity entity = new AccessEventJpaEntity();
        entity.id = event.id();
        entity.siteCode = event.siteCode();
        entity.source = event.source();
        entity.externalEventId = event.externalEventId();
        entity.readerId = event.readerId();
        entity.doorId = event.doorId();
        entity.zoneCode = event.zoneCode();
        entity.personRef = event.personRef();
        entity.kind = event.kind();
        entity.direction = event.direction();
        entity.occurredAt = event.occurredAt();
        RecordMetadata metadata = event.metadata();
        entity.createdBy = metadata.createdBy();
        entity.createdAt = metadata.createdAt();
        entity.lastModifiedBy = metadata.lastModifiedBy();
        entity.lastModifiedAt = metadata.lastModifiedAt();
        entity.recordVersion = metadata.version();
        entity.sourceChannel = metadata.sourceChannel();
        entity.correlationId = metadata.correlationId();
        return entity;
    }

    public AccessEvent toDomain() {
        RecordMetadata metadata = RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt,
                recordVersion, sourceChannel, correlationId);
        return new AccessEvent(id, siteCode, source, externalEventId, readerId, doorId, zoneCode, personRef, kind,
                direction, occurredAt, metadata);
    }

    public UUID getId() {
        return id;
    }
}
