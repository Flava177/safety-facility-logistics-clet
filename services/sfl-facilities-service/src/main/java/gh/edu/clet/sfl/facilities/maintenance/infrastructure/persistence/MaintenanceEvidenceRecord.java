package gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.maintenance.domain.EvidenceType;
import gh.edu.clet.sfl.facilities.maintenance.domain.MaintenanceEvidence;
import gh.edu.clet.sfl.facilities.maintenance.domain.RetentionClass;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping for {@link MaintenanceEvidence}.
 *
 * <p>No blob column, and there never will be one: the architecture standard stores evidence by
 * reference. {@code content_hash} is {@code VARCHAR(64)} with a length check in the migration rather
 * than {@code CHAR(64)}, which Hibernate rejects at validation — a defect the S152 round found by
 * running against a real database.
 */
@Entity
@Table(name = "maintenance_evidence", schema = "facilities")
public class MaintenanceEvidenceRecord {

    @Id
    private UUID id;
    @Column(name = "work_order_id", nullable = false)
    private UUID workOrderId;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 30)
    private EvidenceType evidenceType;
    @Column(name = "file_reference", nullable = false, length = 500)
    private String fileReference;
    @Column(name = "file_name", length = 300)
    private String fileName;
    @Column(name = "media_type", length = 120)
    private String mediaType;
    @Column(name = "size_bytes")
    private Long sizeBytes;
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;
    @Enumerated(EnumType.STRING)
    @Column(name = "retention_class", nullable = false, length = 30)
    private RetentionClass retentionClass;
    @Column(name = "legal_hold", nullable = false)
    private boolean legalHold;
    @Column(length = 2000)
    private String notes;
    @Column(name = "uploaded_by", nullable = false, length = 160)
    private String uploadedBy;
    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected MaintenanceEvidenceRecord() {
    }

    public static MaintenanceEvidenceRecord from(MaintenanceEvidence evidence) {
        MaintenanceEvidenceRecord record = new MaintenanceEvidenceRecord();
        record.apply(evidence);
        return record;
    }

    public void apply(MaintenanceEvidence evidence) {
        id = evidence.id();
        workOrderId = evidence.workOrderId();
        siteCode = evidence.siteCode();
        evidenceType = evidence.evidenceType();
        fileReference = evidence.fileReference();
        fileName = evidence.fileName();
        mediaType = evidence.mediaType();
        sizeBytes = evidence.sizeBytes();
        contentHash = evidence.contentHash();
        retentionClass = evidence.retentionClass();
        legalHold = evidence.legalHold();
        notes = evidence.notes();
        uploadedBy = evidence.uploadedBy();
        uploadedAt = evidence.uploadedAt();
    }

    public MaintenanceEvidence toDomain() {
        return new MaintenanceEvidence(id, workOrderId, siteCode, evidenceType, fileReference, fileName,
                mediaType, sizeBytes, contentHash, retentionClass, legalHold, notes, uploadedBy, uploadedAt);
    }
}
