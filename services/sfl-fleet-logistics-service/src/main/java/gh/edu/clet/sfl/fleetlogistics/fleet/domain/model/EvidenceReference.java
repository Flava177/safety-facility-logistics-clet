package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RetentionClassMissingException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Metadata-only reference to retained evidence (SRS-SFL-S166-03). */
public record EvidenceReference(
        UUID id,
        SiteCode siteCode,
        String relatedRecordType,
        String relatedRecordId,
        String evidenceType,
        String fileName,
        String contentType,
        String storageReference,
        String sha256Hash,
        EvidenceRetentionClass retentionClass,
        Instant retentionExpiresAt,
        boolean legalHold,
        RecordMetadata metadata) {

    public EvidenceReference {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(siteCode, "siteCode is required");
        Objects.requireNonNull(metadata, "metadata is required");
        relatedRecordType = requireText(relatedRecordType, "relatedRecordType");
        relatedRecordId = requireText(relatedRecordId, "relatedRecordId");
        evidenceType = requireText(evidenceType, "evidenceType");
        fileName = requireText(fileName, "fileName");
        contentType = requireText(contentType, "contentType");
        storageReference = requireText(storageReference, "storageReference");
        sha256Hash = requireText(sha256Hash, "sha256Hash");
        if (retentionClass == null) {
            throw new RetentionClassMissingException(Map.of(
                    "resourceType", "EvidenceReference",
                    "resourceId", id.toString(),
                    "siteCode", siteCode.value()));
        }
        if (retentionExpiresAt != null && retentionExpiresAt.isBefore(metadata.createdAt())) {
            throw new IllegalArgumentException("retentionExpiresAt cannot precede createdAt");
        }
    }

    public static EvidenceReference register(UUID id, SiteCode siteCode, String relatedRecordType,
            String relatedRecordId, String evidenceType, String fileName, String contentType,
            String storageReference, String sha256Hash, EvidenceRetentionClass retentionClass,
            Instant retentionExpiresAt, ActorStamp stamp) {
        return new EvidenceReference(id, siteCode, relatedRecordType, relatedRecordId, evidenceType, fileName,
                contentType, storageReference, sha256Hash, retentionClass, retentionExpiresAt,
                retentionClass == EvidenceRetentionClass.LEGAL_HOLD,
                RecordMetadata.createdBy(stamp.actorId(), stamp.now(), stamp.sourceChannel(),
                        stamp.correlationId()));
    }

    public Map<String, Object> auditImage() {
        return Map.ofEntries(
                Map.entry("evidenceId", id.toString()),
                Map.entry("siteCode", siteCode.value()),
                Map.entry("relatedRecordType", relatedRecordType),
                Map.entry("relatedRecordId", relatedRecordId),
                Map.entry("evidenceType", evidenceType),
                Map.entry("fileName", fileName),
                Map.entry("contentType", contentType),
                Map.entry("storageReference", storageReference),
                Map.entry("sha256Hash", sha256Hash),
                Map.entry("retentionClass", retentionClass.name()),
                Map.entry("retentionExpiresAt", retentionExpiresAt == null ? "" : retentionExpiresAt.toString()),
                Map.entry("legalHold", legalHold),
                Map.entry("version", metadata.version()));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }

    /** Minimal stamp needed by domain factories without depending on the shared security package. */
    public record ActorStamp(String actorId, Instant now, SourceChannel sourceChannel, String correlationId) {
        public ActorStamp {
            actorId = requireText(actorId, "actorId");
            Objects.requireNonNull(now, "now is required");
            Objects.requireNonNull(sourceChannel, "sourceChannel is required");
        }
    }
}
