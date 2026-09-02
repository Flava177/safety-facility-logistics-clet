package gh.edu.clet.sfl.safetysecurity.incident.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.incident.domain.model.IncidentEvidence;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.RetentionClass;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link IncidentEvidence}. */
@Entity
@Table(name = "incident_evidence", schema = "safety_security")
public class IncidentEvidenceJpaEntity {

    @Id
    private UUID id;
    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;
    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;
    @Column(name = "file_reference", nullable = false, length = 500)
    private String fileReference;
    @Column(name = "file_name", length = 255)
    private String fileName;
    @Column(name = "media_type", length = 120)
    private String mediaType;
    @Column(name = "size_bytes")
    private Long sizeBytes;
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;
    @Enumerated(EnumType.STRING)
    @Column(name = "retention_class", nullable = false, length = 20)
    private RetentionClass retentionClass;
    @Column(length = 2000)
    private String notes;
    @Column(name = "uploaded_by", nullable = false, length = 160)
    private String uploadedBy;
    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected IncidentEvidenceJpaEntity() {
    }

    public static IncidentEvidenceJpaEntity from(IncidentEvidence evidence) {
        IncidentEvidenceJpaEntity entity = new IncidentEvidenceJpaEntity();
        entity.apply(evidence);
        return entity;
    }

    public void apply(IncidentEvidence evidence) {
        id = evidence.id();
        incidentId = evidence.incidentId();
        siteCode = evidence.siteCode();
        fileReference = evidence.fileReference();
        fileName = evidence.fileName();
        mediaType = evidence.mediaType();
        sizeBytes = evidence.sizeBytes();
        contentHash = evidence.contentHash();
        retentionClass = evidence.retentionClass();
        notes = evidence.notes();
        uploadedBy = evidence.uploadedBy();
        uploadedAt = evidence.uploadedAt();
    }

    public IncidentEvidence toDomain() {
        return new IncidentEvidence(id, incidentId, siteCode, fileReference, fileName, mediaType, sizeBytes,
                contentHash, retentionClass, notes, uploadedBy, uploadedAt);
    }
}
