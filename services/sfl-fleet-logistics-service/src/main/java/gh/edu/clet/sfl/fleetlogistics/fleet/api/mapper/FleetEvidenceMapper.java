package gh.edu.clet.sfl.fleetlogistics.fleet.api.mapper;

import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.FleetEvidenceResponses.AuditChainVerificationResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.FleetEvidenceResponses.EvidenceResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.FleetEvidenceResponses.ExportRequestResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditChainVerification;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceExportRequest;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceReference;
import org.springframework.stereotype.Component;

/** Maps evidence and audit domain objects to API DTOs. */
@Component
public class FleetEvidenceMapper {

    public EvidenceResponse toResponse(EvidenceReference evidence) {
        return new EvidenceResponse(evidence.id(), evidence.siteCode().value(), evidence.relatedRecordType(),
                evidence.relatedRecordId(), evidence.evidenceType(), evidence.fileName(), evidence.contentType(),
                evidence.storageReference(), evidence.sha256Hash(), evidence.retentionClass(),
                evidence.retentionExpiresAt(), evidence.legalHold(), evidence.metadata().createdBy(),
                evidence.metadata().createdAt(), evidence.metadata().lastModifiedBy(),
                evidence.metadata().lastModifiedAt(), evidence.metadata().version(),
                evidence.metadata().sourceChannel().name(), evidence.metadata().auditCorrelationId());
    }

    public ExportRequestResponse toResponse(EvidenceExportRequest request) {
        return new ExportRequestResponse(request.id(), request.evidenceId(), request.siteCode().value(),
                request.reason(), request.status(), request.requestedBy(), request.requestedAt(),
                request.decidedBy(), request.decidedAt(), request.decisionReason(), request.exportedBy(),
                request.exportedAt(), request.metadata().version());
    }

    public AuditChainVerificationResponse toResponse(AuditChainVerification verification) {
        return new AuditChainVerificationResponse(verification.intact(), verification.recordsChecked(),
                verification.firstDivergentSequence(), verification.expectedValue(), verification.actualValue(),
                verification.reason(), verification.headHash());
    }
}
