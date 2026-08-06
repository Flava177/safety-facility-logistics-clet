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

    /**
     * Maps one record, given whether its bytes are held.
     *
     * <p>The flag is passed in rather than looked up here: a mapper that queries is a mapper that
     * turns a page of twenty rows into twenty round trips, and the caller can answer for a whole page
     * in one.
     */
    public EvidenceResponse toResponse(EvidenceReference evidence, boolean hasContent) {
        return new EvidenceResponse(evidence.id(), evidence.siteCode().value(), evidence.relatedRecordType(),
                evidence.relatedRecordId(), evidence.evidenceType(), evidence.fileName(), evidence.contentType(),
                evidence.storageReference(), evidence.sha256Hash(), evidence.retentionClass(),
                evidence.retentionExpiresAt(), evidence.legalHold(), hasContent, evidence.metadata().createdBy(),
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
