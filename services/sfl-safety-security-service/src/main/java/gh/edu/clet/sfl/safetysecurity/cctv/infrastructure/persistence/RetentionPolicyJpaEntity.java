package gh.edu.clet.sfl.safetysecurity.cctv.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.RecordMetadata;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.RetentionPolicy;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.RetentionScope;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.SourceChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link RetentionPolicy}. */
@Entity
@Table(name = "cctv_retention_policies", schema = "safety_security")
public class RetentionPolicyJpaEntity {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RetentionScope scope;
    @Column(name = "scope_ref", nullable = false, length = 120)
    private String scopeRef;
    @Column(name = "retention_days", nullable = false)
    private int retentionDays;
    @Column(name = "legal_hold", nullable = false)
    private boolean legalHold;
    @Column(name = "legal_hold_reason", length = 2000)
    private String legalHoldReason;
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

    protected RetentionPolicyJpaEntity() {
    }

    public void apply(RetentionPolicy policy) {
        id = policy.id();
        siteCode = policy.siteCode();
        scope = policy.scope();
        scopeRef = policy.scopeRef();
        retentionDays = policy.retentionDays();
        legalHold = policy.legalHold();
        legalHoldReason = policy.legalHoldReason();
        RecordMetadata metadata = policy.metadata();
        createdBy = metadata.createdBy();
        createdAt = metadata.createdAt();
        lastModifiedBy = metadata.lastModifiedBy();
        lastModifiedAt = metadata.lastModifiedAt();
        sourceChannel = metadata.sourceChannel();
        correlationId = metadata.correlationId();
    }

    public RetentionPolicy toDomain() {
        RecordMetadata metadata = RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt,
                recordVersion, sourceChannel, correlationId);
        return new RetentionPolicy(id, siteCode, scope, scopeRef, retentionDays, legalHold, legalHoldReason,
                metadata);
    }

    public UUID getId() {
        return id;
    }
}
