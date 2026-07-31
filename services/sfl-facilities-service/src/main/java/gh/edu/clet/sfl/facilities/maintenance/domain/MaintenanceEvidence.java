package gh.edu.clet.sfl.facilities.maintenance.domain;

import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Evidence attached to a work order — by reference, never by value.
 *
 * <p>The architecture standard is explicit: <em>store references and hashes, not raw video or large
 * files</em>. So this record holds where the file is, what it hashed to when it was accepted, who
 * put it there and how long it must be kept. The bytes live in the document/object-storage service.
 *
 * <p>That is not only a storage decision. SRS-SFL-S153-03 requires evidence to be tamper-evident,
 * and a hash recorded at upload gives that property without this service ever holding the file: if
 * the stored object no longer hashes to {@link #contentHash}, the object changed after CLET accepted
 * it, and the audit trail can say so with a date.
 *
 * <p>{@link #legalHold} sits beside the retention class rather than inside it. A hold is a temporary
 * override that must eventually be lifted, and modelling it as a retention class would lose the
 * original classification the moment the hold was applied — leaving nothing to return to.
 */
public record MaintenanceEvidence(
        UUID id,
        UUID workOrderId,
        String siteCode,
        EvidenceType evidenceType,
        String fileReference,
        String fileName,
        String mediaType,
        Long sizeBytes,
        String contentHash,
        RetentionClass retentionClass,
        boolean legalHold,
        String notes,
        String uploadedBy,
        Instant uploadedAt) {

    /** Lower-case hex SHA-256. Upper-case is accepted on the way in and normalised. */
    private static final Pattern SHA_256_HEX = Pattern.compile("^[0-9a-f]{64}$");

    public MaintenanceEvidence {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(workOrderId, "workOrderId is required");
        siteCode = EstateCodes.normalize(siteCode);
        Objects.requireNonNull(evidenceType, "evidenceType is required");
        EstateCodes.require(fileReference, "fileReference");
        fileReference = fileReference.strip();
        fileName = EstateCodes.blankToNull(fileName);
        mediaType = EstateCodes.blankToNull(mediaType);
        notes = EstateCodes.blankToNull(notes);
        EstateCodes.require(uploadedBy, "uploadedBy");
        uploadedBy = uploadedBy.strip();
        Objects.requireNonNull(uploadedAt, "uploadedAt is required");
        // SRS-SFL-S153-03: "Retention class is mandatory". Named as its own error state rather than a
        // generic validation failure, because the caller needs to be told which field and why.
        if (retentionClass == null) {
            throw new FacilitiesException.RetentionClassMissingException();
        }
        EstateCodes.require(contentHash, "contentHash");
        contentHash = contentHash.strip().toLowerCase(java.util.Locale.ROOT);
        if (!SHA_256_HEX.matcher(contentHash).matches()) {
            throw new IllegalArgumentException("contentHash must be a 64-character hex SHA-256 digest");
        }
        if (sizeBytes != null && sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes cannot be negative");
        }
    }

    public static MaintenanceEvidence attach(UUID id, UUID workOrderId, String siteCode, EvidenceType type,
            String fileReference, String fileName, String mediaType, Long sizeBytes, String contentHash,
            RetentionClass retentionClass, String notes, String uploadedBy, Instant uploadedAt) {
        return new MaintenanceEvidence(id, workOrderId, siteCode, type, fileReference, fileName, mediaType,
                sizeBytes, contentHash, retentionClass, false, notes, uploadedBy, uploadedAt);
    }

    public MaintenanceEvidence withLegalHold(boolean held) {
        return held == legalHold
                ? this
                : new MaintenanceEvidence(id, workOrderId, siteCode, evidenceType, fileReference, fileName,
                        mediaType, sizeBytes, contentHash, retentionClass, held, notes, uploadedBy, uploadedAt);
    }

    /** The earliest date this may be disposed of. {@code null} while a legal hold is in force. */
    public LocalDate disposalEligibleFrom() {
        return legalHold ? null : retentionClass.disposalEligibleFrom(
                uploadedAt.atZone(ZoneOffset.UTC).toLocalDate());
    }

    /** {@code true} when this item satisfies a closure-evidence requirement. */
    public boolean supportsClosure() {
        return evidenceType != EvidenceType.INVOICE;
    }
}
