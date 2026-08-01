package gh.edu.clet.sfl.fleetlogistics.fleet.api.response;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceRetentionClass;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A vehicle compliance document, with the days-remaining figure the dashboard and dashboard need. */
public record ComplianceDocumentResponse(
        UUID id,
        UUID vehicleId,
        String siteCode,
        ComplianceDocumentType documentType,
        boolean mandatory,
        String documentReference,
        String issuingAuthority,
        LocalDate issuedOn,
        LocalDate expiresOn,
        long daysUntilExpiry,
        ComplianceDocumentStatus status,
        UUID evidenceId,
        EvidenceRetentionClass retentionClass,
        Instant createdAt,
        String createdBy,
        long version) {
}
