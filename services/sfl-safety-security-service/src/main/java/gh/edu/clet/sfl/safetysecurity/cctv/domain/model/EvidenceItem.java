package gh.edu.clet.sfl.safetysecurity.cctv.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Footage recorded by reference - SRS-SFL-S161-03/04: "each exported evidence item [is recorded] by
 * reference ... with a cryptographic hash and provenance, and attaches it to the relevant incident or
 * investigation case." By default {@link #copiedRawVideo} is {@code false}: SFL holds only the
 * reference, a hash and provenance, never the video bytes, unless a retention/privacy exception is
 * explicitly recorded (SRS-SFL-S161-04).
 *
 * @param exportHandle the VMS's own reference/handle for this clip - an opaque string, never a URL or
 *        credential; retrieving the bytes it names is the vendor's job, not SFL's.
 * @param hash a cryptographic hash computed by the vendor gateway at export time.
 * @param caseRef the S163 case this evidence is attached to, held by value.
 */
public record EvidenceItem(UUID id, UUID requestId, String siteCode, String cameraId, Instant windowStart,
        Instant windowEnd, String exportHandle, String hash, String provenance, UUID caseRef,
        boolean copiedRawVideo, String copyApprovalRef, EvidenceItemStatus status, RecordMetadata metadata) {

    public EvidenceItem {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(requestId, "requestId is required");
        require(siteCode, "siteCode");
        require(cameraId, "cameraId");
        Objects.requireNonNull(windowStart, "windowStart is required");
        Objects.requireNonNull(windowEnd, "windowEnd is required");
        require(exportHandle, "exportHandle");
        require(hash, "hash");
        require(provenance, "provenance");
        copyApprovalRef = blankToNull(copyApprovalRef);
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static EvidenceItem record(UUID id, UUID requestId, String siteCode, String cameraId, Instant windowStart,
            Instant windowEnd, String exportHandle, String hash, String provenance, UUID caseRef, String actorId,
            Instant at, SourceChannel channel, String correlationId) {
        return new EvidenceItem(id, requestId, siteCode, cameraId, windowStart, windowEnd, exportHandle, hash,
                provenance, caseRef, false, null, EvidenceItemStatus.ACTIVE,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    public EvidenceItem purge(String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new EvidenceItem(id, requestId, siteCode, cameraId, windowStart, windowEnd, exportHandle, hash,
                provenance, caseRef, copiedRawVideo, copyApprovalRef, EvidenceItemStatus.PURGED,
                metadata.modifiedBy(actorId, at, channel, correlationId));
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
