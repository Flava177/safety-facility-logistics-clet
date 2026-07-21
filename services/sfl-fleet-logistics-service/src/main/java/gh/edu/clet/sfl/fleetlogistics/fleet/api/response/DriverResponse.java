package gh.edu.clet.sfl.fleetlogistics.fleet.api.response;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverEligibilityStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverLifecycleStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.LicenceClass;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Driver profile reference as returned by the API.
 *
 * <p>{@code licenceNumber} is masked unless the caller holds {@code FLEET_DRIVER_SENSITIVE_READ};
 * {@code licenceNumberMasked} says which form was returned so a console never presents a masked value
 * as the real one.
 */
public record DriverResponse(
        UUID id,
        String staffReference,
        String displayName,
        String licenceNumber,
        boolean licenceNumberMasked,
        LicenceClass licenceClass,
        LocalDate licenceExpiresOn,
        long daysUntilLicenceExpiry,
        LocalDate medicalClearanceExpiresOn,
        String siteCode,
        String responsibleUnit,
        DriverLifecycleStatus lifecycleStatus,
        DriverEligibilityStatus eligibilityStatus,
        String suspensionReason,
        String createdBy,
        Instant createdAt,
        String lastModifiedBy,
        Instant lastModifiedAt,
        long version) {
}
