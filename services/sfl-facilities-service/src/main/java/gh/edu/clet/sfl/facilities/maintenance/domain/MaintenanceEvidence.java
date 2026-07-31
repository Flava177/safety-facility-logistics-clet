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
        Instant uploadedAt,
        Instant disposedAt,
        String disposalReason) {

    /** Lower-case hex SHA-256. Upper-case is accepted on the way in and normalised. */
    private static final Pattern SHA_256_HEX = Pattern.compile("^[0-9a-f]{64}$");

    public MaintenanceEvidence {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(workOrderId, "workOrderId is required");
        siteCode = EstateCodes.normalize(siteCode);
        Objects.requireNonNull(evidenceType, "evidenceType is required");
        // Null once disposed, and only then: the pointer is what disposal removes, and a record with
        // neither a pointer nor a disposal date would be evidence that had simply vanished.
        if (disposedAt == null) {
            EstateCodes.require(fileReference, "fileReference");
            fileReference = fileReference.strip();
            if (disposalReason != null) {
                throw new IllegalArgumentException("a disposal reason without a disposal date is not a state");
            }
        } else {
            if (fileReference != null && !fileReference.isBlank()) {
                throw new IllegalArgumentException("disposed evidence must not keep its file reference");
            }
            fileReference = null;
            EstateCodes.require(disposalReason, "disposalReason");
            disposalReason = disposalReason.strip();
        }
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
                sizeBytes, contentHash, retentionClass, false, notes, uploadedBy, uploadedAt, null, null);
    }

    public MaintenanceEvidence withLegalHold(boolean held) {
        return held == legalHold
                ? this
                : new MaintenanceEvidence(id, workOrderId, siteCode, evidenceType, fileReference, fileName,
                        mediaType, sizeBytes, contentHash, retentionClass, held, notes, uploadedBy, uploadedAt,
                        disposedAt, disposalReason);
    }

    /** The earliest date this may be disposed of. {@code null} while a legal hold is in force. */
    public LocalDate disposalEligibleFrom() {
        return legalHold ? null : retentionClass.disposalEligibleFrom(
                uploadedAt.atZone(ZoneOffset.UTC).toLocalDate());
    }

    /**
     * Whether the retention period has run out and nothing is holding this back.
     *
     * <p>A legal hold beats the clock, always, and outlives it: evidence held past its retention date
     * stays until the hold is lifted, at which point it becomes eligible immediately rather than
     * restarting a period. That is what a hold is for.
     */
    public boolean isDisposalEligible(LocalDate today) {
        if (disposedAt != null || legalHold) {
            return false;
        }
        LocalDate eligibleFrom = disposalEligibleFrom();
        return eligibleFrom != null && !today.isBefore(eligibleFrom);
    }

    /**
     * Disposes of the reference, keeping the record that it existed.
     *
     * <p>The row survives with its hash, its retention class and who uploaded it, because a retention
     * policy must be able to prove two different things: that a thing was destroyed when it should have
     * been, and that it existed and was destroyed for a stated reason. Deleting the row proves neither,
     * and leaves an auditor unable to tell disposal from the evidence never having been captured.
     *
     * <p>Refuses a held item rather than silently skipping it, because "the sweep did not dispose this"
     * and "the sweep could not dispose this" must not look the same from the outside.
     */
    public MaintenanceEvidence disposed(Instant at, String reason) {
        if (legalHold) {
            throw new IllegalStateException("Evidence under legal hold cannot be disposed of");
        }
        if (disposedAt != null) {
            return this;
        }
        return new MaintenanceEvidence(id, workOrderId, siteCode, evidenceType, null, fileName, mediaType,
                sizeBytes, contentHash, retentionClass, false, notes, uploadedBy, uploadedAt, at, reason);
    }

    /** {@code true} when this item satisfies a closure-evidence requirement. */
    public boolean supportsClosure() {
        return evidenceType != EvidenceType.INVOICE;
    }
}
