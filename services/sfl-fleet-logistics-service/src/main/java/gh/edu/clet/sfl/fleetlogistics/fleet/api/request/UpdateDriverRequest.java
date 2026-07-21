package gh.edu.clet.sfl.fleetlogistics.fleet.api.request;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverLifecycleStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.LicenceClass;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Request body for {@code PATCH /api/v1/fleet/drivers/{driverId}}.
 *
 * <p>{@code targetLifecycleStatus} is optional; supplying {@code SUSPENDED} also requires
 * {@code lifecycleReason}, which the eligibility assessment quotes back to the dispatcher.
 */
public record UpdateDriverRequest(
        @NotBlank @Size(max = 200) String displayName,
        @NotBlank @Size(max = 80) String licenceNumber,
        @NotNull LicenceClass licenceClass,
        @NotNull LocalDate licenceExpiresOn,
        LocalDate medicalClearanceExpiresOn,
        @NotBlank @Size(max = 160) String responsibleUnit,
        DriverLifecycleStatus targetLifecycleStatus,
        @Size(max = 1000) String lifecycleReason,
        Long expectedVersion) {
}
