package gh.edu.clet.sfl.safetysecurity.cctv.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.EvidenceItem;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.EvidenceItemStatus;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.RecordMetadata;
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

/** JPA mapping for {@link EvidenceItem}. */
@Entity
@Table(name = "cctv_evidence_items", schema = "safety_security")
public class EvidenceItemJpaEntity {

    @Id
    private UUID id;
    @Column(name = "request_id", nullable = false)
    private UUID requestId;
    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;
    @Column(name = "camera_id", nullable = false, length = 120)
    private String cameraId;
    @Column(name = "window_start", nullable = false)
    private Instant windowStart;
    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;
    @Column(name = "export_handle", nullable = false, length = 300)
    private String exportHandle;
    @Column(nullable = false, length = 64)
    private String hash;
    @Column(nullable = false, length = 2000)
    private String provenance;
    @Column(name = "case_ref")
    private UUID caseRef;
    @Column(name = "copied_raw_video", nullable = false)
    private boolean copiedRawVideo;
    @Column(name = "copy_approval_ref", length = 200)
    private String copyApprovalRef;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EvidenceItemStatus status;
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

    protected EvidenceItemJpaEntity() {
    }

    public void apply(EvidenceItem item) {
        id = item.id();
        requestId = item.requestId();
        siteCode = item.siteCode();
        cameraId = item.cameraId();
        windowStart = item.windowStart();
        windowEnd = item.windowEnd();
        exportHandle = item.exportHandle();
        hash = item.hash();
        provenance = item.provenance();
        caseRef = item.caseRef();
        copiedRawVideo = item.copiedRawVideo();
        copyApprovalRef = item.copyApprovalRef();
        status = item.status();
        RecordMetadata metadata = item.metadata();
        createdBy = metadata.createdBy();
        createdAt = metadata.createdAt();
        lastModifiedBy = metadata.lastModifiedBy();
        lastModifiedAt = metadata.lastModifiedAt();
        sourceChannel = metadata.sourceChannel();
        correlationId = metadata.correlationId();
    }

    public EvidenceItem toDomain() {
        RecordMetadata metadata = RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt,
                recordVersion, sourceChannel, correlationId);
        return new EvidenceItem(id, requestId, siteCode, cameraId, windowStart, windowEnd, exportHandle, hash,
                provenance, caseRef, copiedRawVideo, copyApprovalRef, status, metadata);
    }

    public UUID getId() {
        return id;
    }
}
