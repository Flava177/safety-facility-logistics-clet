package gh.edu.clet.sfl.fleetlogistics.fleet.api.response;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleAvailabilityStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleCategory;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleLifecycleStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleServiceStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Vehicle representation returned by the API. Never the JPA entity.
 *
 * <p>{@code vin} is masked unless the caller holds {@code FLEET_VEHICLE_SENSITIVE_READ}, and
 * {@code vinMasked} tells the client which it received so the UI can label the field honestly rather
 * than presenting a masked value as the real one (SRS-SFL-S166-01 sensitive-field masking).
 */
public record VehicleResponse(
        UUID id,
        String registrationNumber,
        String vin,
        boolean vinMasked,
        String make,
        String model,
        int manufactureYear,
        VehicleCategory category,
        int capacity,
        String siteCode,
        String responsibleUnit,
        String operationalOwner,
        String acquisitionReference,
        VehicleLifecycleStatus lifecycleStatus,
        VehicleServiceStatus serviceStatus,
        VehicleAvailabilityStatus availabilityStatus,
        long odometerValue,
        String odometerUnit,
        String odometerSource,
        Instant odometerRecordedAt,
        boolean emergencyOnly,
        Set<OperatingMode> allowedOperatingModes,
        UUID currentTripId,
        String createdBy,
        Instant createdAt,
        String lastModifiedBy,
        Instant lastModifiedAt,
        long version,
        String sourceChannel,
        String auditCorrelationId) {
}
