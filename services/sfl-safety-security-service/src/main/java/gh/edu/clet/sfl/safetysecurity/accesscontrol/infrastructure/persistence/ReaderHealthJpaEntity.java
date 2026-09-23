package gh.edu.clet.sfl.safetysecurity.accesscontrol.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.ReaderHealth;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.ReaderHealthStatus;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.RecordMetadata;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.SourceChannel;
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
@Table(name = "access_reader_health", schema = "safety_security")
public class ReaderHealthJpaEntity {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;
    @Column(name = "reader_id", nullable = false, length = 120)
    private String readerId;
    @Column(name = "zone_code", nullable = false, length = 80)
    private String zoneCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReaderHealthStatus status;
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;
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

    protected ReaderHealthJpaEntity() {
    }

    public void apply(ReaderHealth health) {
        id = health.id();
        siteCode = health.siteCode();
        readerId = health.readerId();
        zoneCode = health.zoneCode();
        status = health.status();
        lastSeenAt = health.lastSeenAt();
        RecordMetadata metadata = health.metadata();
        createdBy = metadata.createdBy();
        createdAt = metadata.createdAt();
        lastModifiedBy = metadata.lastModifiedBy();
        lastModifiedAt = metadata.lastModifiedAt();
        sourceChannel = metadata.sourceChannel();
        correlationId = metadata.correlationId();
    }

    public ReaderHealth toDomain() {
        RecordMetadata metadata = RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt,
                recordVersion, sourceChannel, correlationId);
        return new ReaderHealth(id, siteCode, readerId, zoneCode, status, lastSeenAt, metadata);
    }

    public UUID getId() {
        return id;
    }
}
