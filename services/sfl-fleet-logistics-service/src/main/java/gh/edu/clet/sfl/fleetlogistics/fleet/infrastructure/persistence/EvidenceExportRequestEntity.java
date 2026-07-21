package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceExportRequest;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceExportStatus;
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

/** JPA image of an evidence export approval request. */
@Entity
@Table(name = "fleet_evidence_export_requests", schema = "fleet_logistics")
class EvidenceExportRequestEntity {

    @Id
    private UUID id;

    @Column(name = "evidence_id", nullable = false)
    private UUID evidenceId;

    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EvidenceExportStatus status;

    @Column(name = "requested_by", nullable = false, length = 160)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "decided_by", length = 160)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_reason", length = 1000)
    private String decisionReason;

    @Column(name = "exported_by", length = 160)
    private String exportedBy;

    @Column(name = "exported_at")
    private Instant exportedAt;

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

    protected EvidenceExportRequestEntity() {
    }

    static EvidenceExportRequestEntity from(EvidenceExportRequest request) {
        EvidenceExportRequestEntity entity = new EvidenceExportRequestEntity();
        entity.id = request.id();
        entity.applyFrom(request);
        return entity;
    }

    void applyFrom(EvidenceExportRequest request) {
        this.evidenceId = request.evidenceId();
        this.siteCode = request.siteCode().value();
        this.reason = request.reason();
        this.status = request.status();
        this.requestedBy = request.requestedBy();
        this.requestedAt = request.requestedAt();
        this.decidedBy = request.decidedBy();
        this.decidedAt = request.decidedAt();
        this.decisionReason = request.decisionReason();
        this.exportedBy = request.exportedBy();
        this.exportedAt = request.exportedAt();
        this.createdBy = request.metadata().createdBy();
        this.createdAt = request.metadata().createdAt();
        this.lastModifiedBy = request.metadata().lastModifiedBy();
        this.lastModifiedAt = request.metadata().lastModifiedAt();
        this.sourceChannel = request.metadata().sourceChannel();
        this.auditCorrelationId = request.metadata().auditCorrelationId();
    }

    EvidenceExportRequest toDomain() {
        return new EvidenceExportRequest(id, evidenceId, SiteCode.of(siteCode), reason, status, requestedBy,
                requestedAt, decidedBy, decidedAt, decisionReason, exportedBy, exportedAt,
                RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt, version,
                        sourceChannel, auditCorrelationId));
    }
}
