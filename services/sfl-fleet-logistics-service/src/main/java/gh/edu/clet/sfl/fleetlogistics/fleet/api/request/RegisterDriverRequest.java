package gh.edu.clet.sfl.fleetlogistics.fleet.api.request;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.LicenceClass;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Request body for {@code POST /api/v1/fleet/drivers} (SRS-SFL-S166-01). */
public record RegisterDriverRequest(
        @NotBlank @Size(max = 80) String staffReference,
        @NotBlank @Size(max = 200) String displayName,
        @NotBlank @Size(max = 80) String licenceNumber,
        @NotNull LicenceClass licenceClass,
        @NotNull LocalDate licenceExpiresOn,
        LocalDate medicalClearanceExpiresOn,
        @NotBlank @Size(max = 40) String siteCode,
        @NotBlank @Size(max = 160) String responsibleUnit) {
}
