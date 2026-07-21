package gh.edu.clet.sfl.fleetlogistics.fleet.api.request;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RetentionClass;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/fleet/vehicles/{vehicleId}/compliance-documents}.
 *
 * <p>{@code retentionClass} is mandatory — see gap report C-08 for why the stricter reading of the SRS
 * retention rule is applied to all fleet evidence.
 */
public record RegisterComplianceDocumentRequest(
        @NotNull ComplianceDocumentType documentType,
        @NotBlank @Size(max = 160) String documentReference,
        @NotBlank @Size(max = 160) String issuingAuthority,
        @NotNull LocalDate issuedOn,
        @NotNull LocalDate expiresOn,
        UUID evidenceId,
        @NotNull RetentionClass retentionClass) {
}
