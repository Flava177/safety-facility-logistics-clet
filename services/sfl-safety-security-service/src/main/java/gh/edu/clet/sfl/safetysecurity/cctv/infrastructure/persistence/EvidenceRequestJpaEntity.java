package gh.edu.clet.sfl.safetysecurity.cctv.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.EvidenceRequest;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.EvidenceRequestStatus;
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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** JPA mapping for {@link EvidenceRequest}. {@code cameraIds} is stored as a comma-delimited string,
 * not JSON - the same "a handful of ids does not earn a structured format" reasoning
 * {@code AccessZoneJpaEntity} gives for door groups. */
@Entity
@Table(name = "cctv_evidence_requests", schema = "safety_security")
public class EvidenceRequestJpaEntity {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;
    @Column(name = "camera_ids", nullable = false, length = 2000)
    private String cameraIdsText;
    @Column(name = "location_ref", length = 120)
    private String locationRef;
    @Column(name = "window_start", nullable = false)
    private Instant windowStart;
    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;
    @Column(nullable = false, length = 2000)
    private String purpose;
    @Column(name = "case_ref")
    private UUID caseRef;
    @Column(name = "requested_by", nullable = false, length = 160)
    private String requestedBy;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EvidenceRequestStatus status;
    @Column(name = "decided_by", length = 160)
    private String decidedBy;
    @Column(name = "decided_at")
    private Instant decidedAt;
    @Column(name = "decision_notes", length = 2000)
    private String decisionNotes;
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

    protected EvidenceRequestJpaEntity() {
    }

    public void apply(EvidenceRequest request) {
        id = request.id();
        siteCode = request.siteCode();
        cameraIdsText = String.join(",", request.cameraIds());
        locationRef = request.locationRef();
        windowStart = request.windowStart();
        windowEnd = request.windowEnd();
        purpose = request.purpose();
        caseRef = request.caseRef();
        requestedBy = request.requestedBy();
        status = request.status();
        decidedBy = request.decidedBy();
        decidedAt = request.decidedAt();
        decisionNotes = request.decisionNotes();
        RecordMetadata metadata = request.metadata();
        createdBy = metadata.createdBy();
        createdAt = metadata.createdAt();
        lastModifiedBy = metadata.lastModifiedBy();
        lastModifiedAt = metadata.lastModifiedAt();
        sourceChannel = metadata.sourceChannel();
        correlationId = metadata.correlationId();
    }

    public EvidenceRequest toDomain() {
        RecordMetadata metadata = RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt,
                recordVersion, sourceChannel, correlationId);
        List<String> cameraIds = cameraIdsText == null || cameraIdsText.isBlank() ? List.of()
                : Arrays.asList(cameraIdsText.split(","));
        return new EvidenceRequest(id, siteCode, cameraIds, locationRef, windowStart, windowEnd, purpose, caseRef,
                requestedBy, status, decidedBy, decidedAt, decisionNotes, metadata);
    }

    public UUID getId() {
        return id;
    }
}
