package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.ExportNotApprovedException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Approval-controlled evidence export request (SRS-SFL-S166-03). */
public record EvidenceExportRequest(
        UUID id,
        UUID evidenceId,
        SiteCode siteCode,
        String reason,
        EvidenceExportStatus status,
        String requestedBy,
        Instant requestedAt,
        String decidedBy,
        Instant decidedAt,
        String decisionReason,
        String exportedBy,
        Instant exportedAt,
        RecordMetadata metadata) {

    public EvidenceExportRequest {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(evidenceId, "evidenceId is required");
        Objects.requireNonNull(siteCode, "siteCode is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(requestedAt, "requestedAt is required");
        Objects.requireNonNull(metadata, "metadata is required");
        reason = requireText(reason, "reason");
        requestedBy = requireText(requestedBy, "requestedBy");
    }

    public static EvidenceExportRequest request(UUID id, EvidenceReference evidence, String reason,
            EvidenceReference.ActorStamp stamp) {
        return new EvidenceExportRequest(id, evidence.id(), evidence.siteCode(), reason,
                EvidenceExportStatus.REQUESTED, stamp.actorId(), stamp.now(), null, null, null, null, null,
                RecordMetadata.createdBy(stamp.actorId(), stamp.now(), stamp.sourceChannel(), stamp.correlationId()));
    }

    public EvidenceExportRequest decide(boolean approved, String decisionReason, EvidenceReference.ActorStamp stamp) {
        if (requestedBy.equalsIgnoreCase(stamp.actorId())) {
            throw new ExportNotApprovedException(Map.of(
                    "resourceType", "EvidenceExportRequest",
                    "resourceId", id.toString(),
                    "siteCode", siteCode.value(),
                    "reason", "Requester cannot approve their own evidence export."));
        }
        return new EvidenceExportRequest(id, evidenceId, siteCode, reason,
                approved ? EvidenceExportStatus.APPROVED : EvidenceExportStatus.REJECTED,
                requestedBy, requestedAt, stamp.actorId(), stamp.now(), requireText(decisionReason,
                "decisionReason"), null, null,
                metadata.modifiedBy(stamp.actorId(), stamp.now(), stamp.sourceChannel(), stamp.correlationId()));
    }

    public EvidenceExportRequest markExported(EvidenceReference.ActorStamp stamp) {
        if (status != EvidenceExportStatus.APPROVED || decisionReason == null || decisionReason.isBlank()) {
            throw new ExportNotApprovedException(Map.of(
                    "resourceType", "EvidenceExportRequest",
                    "resourceId", id.toString(),
                    "siteCode", siteCode.value(),
                    "status", status.name()));
        }
        return new EvidenceExportRequest(id, evidenceId, siteCode, reason, EvidenceExportStatus.EXPORTED,
                requestedBy, requestedAt, decidedBy, decidedAt, decisionReason, stamp.actorId(), stamp.now(),
                metadata.modifiedBy(stamp.actorId(), stamp.now(), stamp.sourceChannel(), stamp.correlationId()));
    }

    public Map<String, Object> auditImage() {
        return Map.ofEntries(
                Map.entry("exportRequestId", id.toString()),
                Map.entry("evidenceId", evidenceId.toString()),
                Map.entry("siteCode", siteCode.value()),
                Map.entry("reason", reason),
                Map.entry("status", status.name()),
                Map.entry("requestedBy", requestedBy),
                Map.entry("requestedAt", requestedAt.toString()),
                Map.entry("decidedBy", decidedBy == null ? "" : decidedBy),
                Map.entry("decidedAt", decidedAt == null ? "" : decidedAt.toString()),
                Map.entry("decisionReason", decisionReason == null ? "" : decisionReason),
                Map.entry("exportedBy", exportedBy == null ? "" : exportedBy),
                Map.entry("exportedAt", exportedAt == null ? "" : exportedAt.toString()),
                Map.entry("version", metadata.version()));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
