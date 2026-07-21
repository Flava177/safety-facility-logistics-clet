package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceReference;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceRetentionClass;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** JPA image of {@link EvidenceReference}. */
@Entity
@Table(name = "fleet_evidence_references", schema = "fleet_logistics")
class EvidenceReferenceEntity {

    @Id
    private UUID id;

    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;

    @Column(name = "related_record_type", nullable = false, length = 80)
    private String relatedRecordType;

    @Column(name = "related_record_id", nullable = false, length = 160)
    private String relatedRecordId;

    @Column(name = "evidence_type", nullable = false, length = 80)
    private String evidenceType;

    @Column(name = "file_name", nullable = false, length = 240)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "storage_reference", nullable = false, length = 500)
    private String storageReference;

    @Column(name = "sha256_hash", nullable = false, length = 64)
    private String sha256Hash;

    @Enumerated(EnumType.STRING)
    @Column(name = "retention_class", nullable = false, length = 40)
    private EvidenceRetentionClass retentionClass;

    @Column(name = "retention_expires_at")
    private Instant retentionExpiresAt;

    @Column(name = "legal_hold", nullable = false)
    private boolean legalHold;

    @Column(name = "created_by", nullable = false, length = 160)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_modified_by", nullable = false, length = 160)
    private String lastModifiedBy;

    @Column(name = "last_modified_at", nullable = false)
    private Instant lastModifiedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_channel", nullable = false, length = 40)
    private SourceChannel sourceChannel;

    @Column(name = "audit_correlation_id", length = 120)
    private String auditCorrelationId;

    protected EvidenceReferenceEntity() {
    }

    static EvidenceReferenceEntity from(EvidenceReference evidence) {
        EvidenceReferenceEntity entity = new EvidenceReferenceEntity();
        entity.id = evidence.id();
        entity.applyFrom(evidence);
        return entity;
    }

    void applyFrom(EvidenceReference evidence) {
        this.siteCode = evidence.siteCode().value();
        this.relatedRecordType = evidence.relatedRecordType();
        this.relatedRecordId = evidence.relatedRecordId();
        this.evidenceType = evidence.evidenceType();
        this.fileName = evidence.fileName();
        this.contentType = evidence.contentType();
        this.storageReference = evidence.storageReference();
        this.sha256Hash = evidence.sha256Hash();
        this.retentionClass = evidence.retentionClass();
        this.retentionExpiresAt = evidence.retentionExpiresAt();
        this.legalHold = evidence.legalHold();
        this.createdBy = evidence.metadata().createdBy();
        this.createdAt = evidence.metadata().createdAt();
        this.lastModifiedBy = evidence.metadata().lastModifiedBy();
        this.lastModifiedAt = evidence.metadata().lastModifiedAt();
        this.sourceChannel = evidence.metadata().sourceChannel();
        this.auditCorrelationId = evidence.metadata().auditCorrelationId();
    }

    EvidenceReference toDomain() {
        return new EvidenceReference(id, SiteCode.of(siteCode), relatedRecordType, relatedRecordId, evidenceType,
                fileName, contentType, storageReference, sha256Hash, retentionClass, retentionExpiresAt,
                legalHold, RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt,
                version, sourceChannel, auditCorrelationId));
    }
}
