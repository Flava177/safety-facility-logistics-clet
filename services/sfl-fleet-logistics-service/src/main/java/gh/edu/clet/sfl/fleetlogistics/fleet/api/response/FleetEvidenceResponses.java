package gh.edu.clet.sfl.fleetlogistics.fleet.api.response;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceExportStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceRetentionClass;
import java.time.Instant;
import java.util.UUID;

/** HTTP response DTOs for SRS-SFL-S166-03 evidence governance. */
public final class FleetEvidenceResponses {

    private FleetEvidenceResponses() {
    }

    public record EvidenceResponse(
            UUID id,
            String siteCode,
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
            String createdBy,
            Instant createdAt,
            String lastModifiedBy,
            Instant lastModifiedAt,
            long version,
            String sourceChannel,
            String auditCorrelationId) {
    }

    public record ExportRequestResponse(
            UUID id,
            UUID evidenceId,
            String siteCode,
            String reason,
            EvidenceExportStatus status,
            String requestedBy,
            Instant requestedAt,
            String decidedBy,
            Instant decidedAt,
            String decisionReason,
            String exportedBy,
            Instant exportedAt,
            long version) {
    }

    public record AuditChainVerificationResponse(
            boolean intact,
            int recordsChecked,
            Long firstDivergentSequence,
            String expectedValue,
            String actualValue,
            String reason,
            String headHash) {
    }
}
