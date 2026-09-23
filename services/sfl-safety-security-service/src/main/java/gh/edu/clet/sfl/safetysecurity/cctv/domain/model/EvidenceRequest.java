package gh.edu.clet.sfl.safetysecurity.cctv.domain.model;

import gh.edu.clet.sfl.safetysecurity.cctv.domain.exception.CctvErrorCode;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.exception.CctvException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A governed request to retrieve footage - SRS-SFL-S161-02: "the requester selects camera(s), location
 * and time window and states the purpose/case; the request requires approval by an authorised security
 * role before any retrieval or export". No footage is modelled as retrieved until this reaches
 * {@link EvidenceRequestStatus#APPROVED} - see {@code EvidenceRequestService.retrieve}.
 *
 * @param caseRef an S163 {@code SecurityIncident} id this request supports, held by value, or
 *        {@code null} if the request is not yet tied to a case.
 */
public record EvidenceRequest(UUID id, String siteCode, List<String> cameraIds, String locationRef, Instant windowStart,
        Instant windowEnd, String purpose, UUID caseRef, String requestedBy, EvidenceRequestStatus status,
        String decidedBy, Instant decidedAt, String decisionNotes, RecordMetadata metadata) {

    public EvidenceRequest {
        Objects.requireNonNull(id, "id is required");
        require(siteCode, "siteCode");
        if (cameraIds == null || cameraIds.isEmpty()) {
            throw new IllegalArgumentException("cameraIds is required");
        }
        cameraIds = List.copyOf(cameraIds);
        locationRef = blankToNull(locationRef);
        Objects.requireNonNull(windowStart, "windowStart is required");
        Objects.requireNonNull(windowEnd, "windowEnd is required");
        if (!windowEnd.isAfter(windowStart)) {
            throw new IllegalArgumentException("windowEnd must be after windowStart");
        }
        require(purpose, "purpose");
        require(requestedBy, "requestedBy");
        Objects.requireNonNull(status, "status is required");
        decidedBy = blankToNull(decidedBy);
        decisionNotes = blankToNull(decisionNotes);
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static EvidenceRequest create(UUID id, String siteCode, List<String> cameraIds, String locationRef,
            Instant windowStart, Instant windowEnd, String purpose, UUID caseRef, String requestedBy, Instant at,
            SourceChannel channel, String correlationId) {
        return new EvidenceRequest(id, siteCode, cameraIds, locationRef, windowStart, windowEnd, purpose, caseRef,
                requestedBy, EvidenceRequestStatus.PENDING, null, null, null,
                RecordMetadata.createdBy(requestedBy, at, channel, correlationId));
    }

    public EvidenceRequest approve(String approverId, Instant at, SourceChannel channel, String correlationId) {
        requirePending();
        return new EvidenceRequest(id, siteCode, cameraIds, locationRef, windowStart, windowEnd, purpose, caseRef,
                requestedBy, EvidenceRequestStatus.APPROVED, approverId, at, null,
                metadata.modifiedBy(approverId, at, channel, correlationId));
    }

    public EvidenceRequest reject(String approverId, String reason, Instant at, SourceChannel channel,
            String correlationId) {
        requirePending();
        return new EvidenceRequest(id, siteCode, cameraIds, locationRef, windowStart, windowEnd, purpose, caseRef,
                requestedBy, EvidenceRequestStatus.REJECTED, approverId, at, reason,
                metadata.modifiedBy(approverId, at, channel, correlationId));
    }

    private void requirePending() {
        if (status != EvidenceRequestStatus.PENDING) {
            throw CctvException.of(CctvErrorCode.CCTV_EVIDENCE_REQUEST_ALREADY_DECIDED);
        }
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
