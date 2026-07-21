package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.InvalidStateTransitionException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy.VehicleLifecyclePolicy;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The vehicle aggregate — the authoritative, site-scoped fleet record required by SRS-SFL-S166-01.
 *
 * <p>Three statuses are kept apart on purpose. <em>Lifecycle</em> is an administrative decision about
 * the record. <em>Service status</em> is the maintenance standing. <em>Availability</em> is derived
 * from both plus the current assignment. Readiness is not stored at all: it is computed by
 * {@code VehicleReadinessPolicy} from explicit blockers every time it is asked for, because a stored
 * readiness flag goes stale the moment a document expires.
 */
public record Vehicle(
        UUID id,
        RegistrationNumber registrationNumber,
        VehicleIdentificationNumber vin,
        VehicleSpecification specification,
        SiteCode siteCode,
        String responsibleUnit,
        String operationalOwner,
        String acquisitionReference,
        VehicleLifecycleStatus lifecycleStatus,
        VehicleServiceStatus serviceStatus,
        VehicleAvailabilityStatus availabilityStatus,
        OdometerReading odometer,
        RestrictedUse restrictedUse,
        UUID currentTripId,
        RecordMetadata metadata) {

    public Vehicle {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(registrationNumber, "registrationNumber is required");
        Objects.requireNonNull(specification, "specification is required");
        Objects.requireNonNull(siteCode, "siteCode is required");
        Objects.requireNonNull(lifecycleStatus, "lifecycleStatus is required");
        Objects.requireNonNull(serviceStatus, "serviceStatus is required");
        Objects.requireNonNull(availabilityStatus, "availabilityStatus is required");
        Objects.requireNonNull(odometer, "odometer is required");
        Objects.requireNonNull(metadata, "metadata is required");
        responsibleUnit = requireText(responsibleUnit, "responsibleUnit", 160);
        operationalOwner = requireText(operationalOwner, "operationalOwner", 160);
        acquisitionReference = trimToNull(acquisitionReference, 120);
        restrictedUse = restrictedUse == null ? RestrictedUse.unrestricted() : restrictedUse;
    }

    /** Registers a new vehicle. It starts ACTIVE, in service and available. */
    public static Vehicle register(UUID id, RegistrationNumber registrationNumber, VehicleIdentificationNumber vin,
            VehicleSpecification specification, SiteCode siteCode, String responsibleUnit, String operationalOwner,
            String acquisitionReference, OdometerReading odometer, RestrictedUse restrictedUse,
            RecordMetadata metadata) {
        return new Vehicle(id, registrationNumber, vin, specification, siteCode, responsibleUnit, operationalOwner,
                acquisitionReference, VehicleLifecycleStatus.ACTIVE, VehicleServiceStatus.IN_SERVICE,
                VehicleAvailabilityStatus.AVAILABLE, odometer, restrictedUse, null, metadata);
    }

    /** Updates the descriptive attributes an officer may edit. */
    public Vehicle updateDetails(VehicleIdentificationNumber newVin, VehicleSpecification newSpecification,
            String newResponsibleUnit, String newOperationalOwner, String newAcquisitionReference,
            RestrictedUse newRestrictedUse, RecordMetadata newMetadata) {
        VehicleLifecyclePolicy.requireEditable(lifecycleStatus);
        return copy(registrationNumber, newVin, newSpecification, siteCode, newResponsibleUnit, newOperationalOwner,
                newAcquisitionReference, lifecycleStatus, serviceStatus, availabilityStatus, odometer,
                newRestrictedUse, currentTripId, newMetadata);
    }

    /**
     * Applies a lifecycle transition.
     *
     * <p>A vehicle on an active trip cannot be suspended or archived — doing so would strand a trip
     * whose vehicle no longer exists operationally.
     */
    public Vehicle changeLifecycle(VehicleLifecycleStatus target, RecordMetadata newMetadata) {
        VehicleLifecyclePolicy.requireTransition(lifecycleStatus, target);
        if (currentTripId != null && (target == VehicleLifecycleStatus.SUSPENDED
                || target == VehicleLifecycleStatus.ARCHIVED)) {
            throw new InvalidStateTransitionException(Map.of(
                    "aggregate", "Vehicle",
                    "fromStatus", lifecycleStatus.name(),
                    "toStatus", target.name(),
                    "reason", "The vehicle is on an active trip",
                    "currentTripId", currentTripId.toString()));
        }
        VehicleAvailabilityStatus availability = target.isOperational()
                ? recomputeAvailability(serviceStatus, currentTripId)
                : VehicleAvailabilityStatus.UNAVAILABLE;
        return copy(registrationNumber, vin, specification, siteCode, responsibleUnit, operationalOwner,
                acquisitionReference, target, serviceStatus, availability, odometer, restrictedUse, currentTripId,
                newMetadata);
    }

    /** Records a forward odometer movement. Regression raises {@code OdometerRegressionException}. */
    public Vehicle recordOdometer(long reading, OdometerSource source, Instant readAt, RecordMetadata newMetadata) {
        VehicleLifecyclePolicy.requireEditable(lifecycleStatus);
        return withOdometer(odometer.advanceTo(reading, source, readAt), newMetadata);
    }

    /**
     * Applies an authorised odometer correction — the only path that may move a reading backwards.
     * The caller is responsible for having checked the correction permission and for recording the
     * reason and evidence in the audit trail.
     */
    public Vehicle correctOdometer(long correctedReading, Instant correctedAt, RecordMetadata newMetadata) {
        VehicleLifecyclePolicy.requireEditable(lifecycleStatus);
        return withOdometer(odometer.correctTo(correctedReading, correctedAt), newMetadata);
    }

    /** Applies a recomputed service status and the availability that follows from it. */
    public Vehicle withServiceStatus(VehicleServiceStatus newServiceStatus, RecordMetadata newMetadata) {
        Objects.requireNonNull(newServiceStatus, "serviceStatus is required");
        VehicleAvailabilityStatus availability = lifecycleStatus.isOperational()
                ? recomputeAvailability(newServiceStatus, currentTripId)
                : VehicleAvailabilityStatus.UNAVAILABLE;
        return copy(registrationNumber, vin, specification, siteCode, responsibleUnit, operationalOwner,
                acquisitionReference, lifecycleStatus, newServiceStatus, availability, odometer, restrictedUse,
                currentTripId, newMetadata);
    }

    /** Links the vehicle to a trip and marks it assigned. */
    public Vehicle assignToTrip(UUID tripId, RecordMetadata newMetadata) {
        Objects.requireNonNull(tripId, "tripId is required");
        VehicleLifecyclePolicy.requireEditable(lifecycleStatus);
        if (!lifecycleStatus.isOperational()) {
            throw new InvalidStateTransitionException(Map.of(
                    "aggregate", "Vehicle",
                    "fromStatus", lifecycleStatus.name(),
                    "toStatus", VehicleAvailabilityStatus.ASSIGNED.name(),
                    "reason", "Only an active vehicle can be assigned"));
        }
        return copy(registrationNumber, vin, specification, siteCode, responsibleUnit, operationalOwner,
                acquisitionReference, lifecycleStatus, serviceStatus, VehicleAvailabilityStatus.ASSIGNED, odometer,
                restrictedUse, tripId, newMetadata);
    }

    /** Marks the vehicle as being driven on its current trip. */
    public Vehicle markInUse(RecordMetadata newMetadata) {
        if (currentTripId == null) {
            throw new InvalidStateTransitionException(Map.of(
                    "aggregate", "Vehicle",
                    "fromStatus", availabilityStatus.name(),
                    "toStatus", VehicleAvailabilityStatus.IN_USE.name(),
                    "reason", "The vehicle has no current trip"));
        }
        return copy(registrationNumber, vin, specification, siteCode, responsibleUnit, operationalOwner,
                acquisitionReference, lifecycleStatus, serviceStatus, VehicleAvailabilityStatus.IN_USE, odometer,
                restrictedUse, currentTripId, newMetadata);
    }

    /** Releases the vehicle when its trip completes or is cancelled. */
    public Vehicle releaseFromTrip(RecordMetadata newMetadata) {
        VehicleAvailabilityStatus availability = lifecycleStatus.isOperational()
                ? recomputeAvailability(serviceStatus, null)
                : VehicleAvailabilityStatus.UNAVAILABLE;
        return copy(registrationNumber, vin, specification, siteCode, responsibleUnit, operationalOwner,
                acquisitionReference, lifecycleStatus, serviceStatus, availability, odometer, restrictedUse, null,
                newMetadata);
    }

    public boolean isOnTrip() {
        return currentTripId != null;
    }

    /** Availability follows from lifecycle, service standing and whether a trip holds the vehicle. */
    private static VehicleAvailabilityStatus recomputeAvailability(VehicleServiceStatus service, UUID tripId) {
        if (service.blocksAssignment()) {
            return VehicleAvailabilityStatus.UNAVAILABLE;
        }
        return tripId == null ? VehicleAvailabilityStatus.AVAILABLE : VehicleAvailabilityStatus.ASSIGNED;
    }

    private Vehicle withOdometer(OdometerReading newOdometer, RecordMetadata newMetadata) {
        return copy(registrationNumber, vin, specification, siteCode, responsibleUnit, operationalOwner,
                acquisitionReference, lifecycleStatus, serviceStatus, availabilityStatus, newOdometer, restrictedUse,
                currentTripId, newMetadata);
    }

    private Vehicle copy(RegistrationNumber newRegistrationNumber, VehicleIdentificationNumber newVin,
            VehicleSpecification newSpecification, SiteCode newSiteCode, String newResponsibleUnit,
            String newOperationalOwner, String newAcquisitionReference, VehicleLifecycleStatus newLifecycleStatus,
            VehicleServiceStatus newServiceStatus, VehicleAvailabilityStatus newAvailabilityStatus,
            OdometerReading newOdometer, RestrictedUse newRestrictedUse, UUID newCurrentTripId,
            RecordMetadata newMetadata) {
        return new Vehicle(id, newRegistrationNumber, newVin, newSpecification, newSiteCode, newResponsibleUnit,
                newOperationalOwner, newAcquisitionReference, newLifecycleStatus, newServiceStatus,
                newAvailabilityStatus, newOdometer, newRestrictedUse, newCurrentTripId, newMetadata);
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String stripped = value.strip();
        if (stripped.length() > maxLength) {
            throw new IllegalArgumentException(field + " cannot exceed " + maxLength + " characters");
        }
        return stripped;
    }

    private static String trimToNull(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String stripped = value.strip();
        if (stripped.length() > maxLength) {
            throw new IllegalArgumentException("value cannot exceed " + maxLength + " characters");
        }
        return stripped;
    }
}
