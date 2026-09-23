package gh.edu.clet.sfl.safetysecurity.lifesafety.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.ComplianceExceptionKind;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.ComplianceExceptionStatus;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.ComplianceRefType;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.LifeSafetyComplianceException;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.RecordMetadata;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.SourceChannel;
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
@Table(name = "lifesafety_compliance_exceptions", schema = "safety_security")
public class LifeSafetyComplianceExceptionJpaEntity {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;
    @Enumerated(EnumType.STRING)
    @Column(name = "ref_type", nullable = false, length = 30)
    private ComplianceRefType refType;
    @Column(name = "ref_id")
    private UUID refId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ComplianceExceptionKind kind;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ComplianceExceptionStatus status;
    @Column(length = 2000)
    private String note;
    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt;
    @Column(name = "resolved_by", length = 160)
    private String resolvedBy;
    @Column(name = "resolved_at")
    private Instant resolvedAt;
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

    protected LifeSafetyComplianceExceptionJpaEntity() {
    }

    public static LifeSafetyComplianceExceptionJpaEntity from(LifeSafetyComplianceException x) {
        LifeSafetyComplianceExceptionJpaEntity e = new LifeSafetyComplianceExceptionJpaEntity();
        e.apply(x);
        return e;
    }

    public void apply(LifeSafetyComplianceException x) {
        id = x.id();
        siteCode = x.siteCode();
        refType = x.refType();
        refId = x.refId();
        kind = x.kind();
        status = x.status();
        note = x.note();
        raisedAt = x.raisedAt();
        resolvedBy = x.resolvedBy();
        resolvedAt = x.resolvedAt();
        createdBy = x.metadata().createdBy();
        createdAt = x.metadata().createdAt();
        lastModifiedBy = x.metadata().lastModifiedBy();
        lastModifiedAt = x.metadata().lastModifiedAt();
        sourceChannel = x.metadata().sourceChannel();
        correlationId = x.metadata().correlationId();
    }

    public LifeSafetyComplianceException toDomain() {
        RecordMetadata metadata = RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt,
                recordVersion, sourceChannel, correlationId);
        return new LifeSafetyComplianceException(id, siteCode, refType, refId, kind, status, note, raisedAt,
                resolvedBy, resolvedAt, metadata);
    }
}
