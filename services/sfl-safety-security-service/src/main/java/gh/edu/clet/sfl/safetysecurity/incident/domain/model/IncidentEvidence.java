package gh.edu.clet.sfl.safetysecurity.incident.domain.model;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Evidence attached to a {@link SecurityIncident} - by reference, never by value, mirroring
 * facilities' {@code MaintenanceEvidence}: SRS §D.9 step 5, "preserves evidence... with provenance
 * and hashes... large files held by reference to S003". A hash recorded at upload is what lets a
 * later mismatch prove the stored object changed after CLET accepted it.
 */
public record IncidentEvidence(
        UUID id,
        UUID incidentId,
        String siteCode,
        String fileReference,
        String fileName,
        String mediaType,
        Long sizeBytes,
        String contentHash,
        RetentionClass retentionClass,
        String notes,
        String uploadedBy,
        Instant uploadedAt) {

    /** Lower-case hex SHA-256. Upper-case is accepted on the way in and normalised. */
    private static final Pattern SHA_256_HEX = Pattern.compile("^[0-9a-f]{64}$");

    public IncidentEvidence {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(incidentId, "incidentId is required");
        siteCode = normalizeSite(siteCode);
        require(fileReference, "fileReference");
        fileReference = fileReference.strip();
        fileName = blankToNull(fileName);
        mediaType = blankToNull(mediaType);
        if (sizeBytes != null && sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes cannot be negative");
        }
        require(contentHash, "contentHash");
        contentHash = contentHash.strip().toLowerCase(Locale.ROOT);
        if (!SHA_256_HEX.matcher(contentHash).matches()) {
            throw new IllegalArgumentException("contentHash must be a 64-character hex SHA-256 digest");
        }
        Objects.requireNonNull(retentionClass, "retentionClass is required");
        notes = blankToNull(notes);
        require(uploadedBy, "uploadedBy");
        uploadedBy = uploadedBy.strip();
        Objects.requireNonNull(uploadedAt, "uploadedAt is required");
    }

    public static IncidentEvidence attach(UUID id, SecurityIncident incident, String fileReference, String fileName,
            String mediaType, Long sizeBytes, String contentHash, RetentionClass retentionClass, String notes,
            String uploadedBy, Instant uploadedAt) {
        return new IncidentEvidence(id, incident.id(), incident.siteCode(), fileReference, fileName, mediaType,
                sizeBytes, contentHash, retentionClass, notes, uploadedBy, uploadedAt);
    }

    private static String normalizeSite(String siteCode) {
        require(siteCode, "siteCode");
        return siteCode.strip().toUpperCase(Locale.ROOT);
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
