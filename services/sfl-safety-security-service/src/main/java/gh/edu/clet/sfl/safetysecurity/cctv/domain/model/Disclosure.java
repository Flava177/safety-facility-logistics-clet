package gh.edu.clet.sfl.safetysecurity.cctv.domain.model;

import gh.edu.clet.sfl.safetysecurity.cctv.domain.exception.CctvErrorCode;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.exception.CctvException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A governed request to release footage outside CLET - SRS-SFL-S161-05: "disclosure ... follows a
 * governed disclosure workflow with authorised approval, and the purpose and recipient are recorded;
 * every disclosure is logged and hash-referenced." Approval is itself the release event: no separate
 * "disclosed" transition exists because the SRS names no further step after approval.
 */
public record Disclosure(UUID id, UUID evidenceItemId, String siteCode, String purpose, String recipient,
        String requestedBy, DisclosureStatus status, String decidedBy, Instant decidedAt, String hash,
        RecordMetadata metadata) {

    public Disclosure {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(evidenceItemId, "evidenceItemId is required");
        require(siteCode, "siteCode");
        require(purpose, "purpose");
        require(recipient, "recipient");
        require(requestedBy, "requestedBy");
        Objects.requireNonNull(status, "status is required");
        decidedBy = blankToNull(decidedBy);
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static Disclosure request(UUID id, UUID evidenceItemId, String siteCode, String purpose, String recipient,
            String requestedBy, Instant at, SourceChannel channel, String correlationId) {
        return new Disclosure(id, evidenceItemId, siteCode, purpose, recipient, requestedBy,
                DisclosureStatus.REQUESTED, null, null, null,
                RecordMetadata.createdBy(requestedBy, at, channel, correlationId));
    }

    /** SRS-SFL-S161-05: "footage released by reference and hashed" - {@code evidenceHash} is the
     * {@link EvidenceItem}'s own hash, carried onto the disclosure record at the moment it is approved. */
    public Disclosure approve(String approverId, String evidenceHash, Instant at, SourceChannel channel,
            String correlationId) {
        requireRequested();
        return new Disclosure(id, evidenceItemId, siteCode, purpose, recipient, requestedBy,
                DisclosureStatus.APPROVED, approverId, at, evidenceHash,
                metadata.modifiedBy(approverId, at, channel, correlationId));
    }

    public Disclosure reject(String approverId, Instant at, SourceChannel channel, String correlationId) {
        requireRequested();
        return new Disclosure(id, evidenceItemId, siteCode, purpose, recipient, requestedBy,
                DisclosureStatus.REJECTED, approverId, at, null,
                metadata.modifiedBy(approverId, at, channel, correlationId));
    }

    private void requireRequested() {
        if (status != DisclosureStatus.REQUESTED) {
            throw CctvException.of(CctvErrorCode.CCTV_DISCLOSURE_ALREADY_DECIDED);
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
