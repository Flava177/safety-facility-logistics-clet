package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocument;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RetentionClass;
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
import java.time.LocalDate;
import java.util.UUID;

/** Persistence image of {@link ComplianceDocument}. */
@Entity
@Table(name = "vehicle_compliance_documents", schema = "fleet_logistics")
public class ComplianceDocumentEntity {

    @Id
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 60)
    private ComplianceDocumentType documentType;

    @Column(name = "document_reference", nullable = false, length = 160)
    private String documentReference;

    @Column(name = "issuing_authority", nullable = false, length = 160)
    private String issuingAuthority;

    @Column(name = "issued_on", nullable = false)
    private LocalDate issuedOn;

    @Column(name = "expires_on", nullable = false)
    private LocalDate expiresOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ComplianceDocumentStatus status;

    @Column(name = "evidence_id")
    private UUID evidenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "retention_class", nullable = false, length = 40)
    private RetentionClass retentionClass;

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

    protected ComplianceDocumentEntity() {
    }

    public static ComplianceDocumentEntity from(ComplianceDocument document) {
        ComplianceDocumentEntity entity = new ComplianceDocumentEntity();
        entity.id = document.id();
        entity.applyFrom(document);
        return entity;
    }

    public void applyFrom(ComplianceDocument document) {
        this.vehicleId = document.vehicleId();
        this.siteCode = document.siteCode().value();
        this.documentType = document.documentType();
        this.documentReference = document.documentReference();
        this.issuingAuthority = document.issuingAuthority();
        this.issuedOn = document.issuedOn();
        this.expiresOn = document.expiresOn();
        this.status = document.status();
        this.evidenceId = document.evidenceId();
        this.retentionClass = document.retentionClass();
        this.createdBy = document.metadata().createdBy();
        this.createdAt = document.metadata().createdAt();
        this.lastModifiedBy = document.metadata().lastModifiedBy();
        this.lastModifiedAt = document.metadata().lastModifiedAt();
        this.sourceChannel = document.metadata().sourceChannel();
        this.auditCorrelationId = document.metadata().auditCorrelationId();
    }

    public ComplianceDocument toDomain() {
        return new ComplianceDocument(id, vehicleId, SiteCode.of(siteCode), documentType, documentReference,
                issuingAuthority, issuedOn, expiresOn, status, evidenceId, retentionClass,
                RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt, version,
                        sourceChannel, auditCorrelationId));
    }
}
