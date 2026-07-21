package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.ClosureEvidenceMissingException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.InvalidStateTransitionException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.OdometerRegressionException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy.TripTransitionPolicy;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A vehicle and driver assignment for a period — the "assignment" record of SRS-SFL-S166-01 and the
 * workflow of SRS-SFL-S166-02.
 *
 * <p>Closure is the point the SRS guards hardest: it needs a reason, evidence and an end odometer that
 * does not regress. All three are enforced here rather than in a service, so no caller can complete a
 * trip through a side door.
 *
 * <p>{@code statusBeforeHold} exists so resuming returns the trip to whatever it was doing rather than
 * guessing — a trip held while in progress must not resume as merely assigned.
 */
public record Trip(
        UUID id,
        String tripNumber,
        UUID vehicleId,
        UUID driverId,
        SiteCode siteCode,
        String purpose,
        String origin,
        String destination,
        OperatingMode operatingMode,
        DateTimeRange plannedPeriod,
        Instant actualStart,
        Instant actualEnd,
        TripStatus status,
        TripStatus statusBeforeHold,
        String holdReason,
        String cancellationReason,
        String closureReason,
        UUID closureEvidenceId,
        Long startOdometer,
        Long endOdometer,
        RecordMetadata metadata) {

    public Trip {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(siteCode, "siteCode is required");
        Objects.requireNonNull(plannedPeriod, "plannedPeriod is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(metadata, "metadata is required");
        Objects.requireNonNull(operatingMode, "operatingMode is required");
        tripNumber = requireText(tripNumber, "tripNumber", 40);
        purpose = requireText(purpose, "purpose", 500);
        origin = requireText(origin, "origin", 200);
        destination = requireText(destination, "destination", 200);
        if (endOdometer != null && startOdometer != null && endOdometer < startOdometer) {
            throw OdometerRegressionException.of(startOdometer, endOdometer);
        }
    }

    /** Creates a planned trip with no vehicle or driver yet nominated. */
    public static Trip plan(UUID id, String tripNumber, SiteCode siteCode, String purpose, String origin,
            String destination, OperatingMode operatingMode, DateTimeRange plannedPeriod,
            RecordMetadata metadata) {
        return new Trip(id, tripNumber, null, null, siteCode, purpose, origin, destination, operatingMode,
                plannedPeriod, null, null, TripStatus.PLANNED, null, null, null, null, null, null, null,
                metadata);
    }

    /**
     * Assigns or reassigns the vehicle and driver.
     *
     * <p>The caller is responsible for having checked readiness and eligibility first; this method
     * enforces only what the aggregate itself can know.
     */
    public Trip assign(UUID newVehicleId, UUID newDriverId, RecordMetadata newMetadata) {
        Objects.requireNonNull(newVehicleId, "vehicleId is required");
        Objects.requireNonNull(newDriverId, "driverId is required");
        TripTransitionPolicy.requireTransition(status, TripStatus.ASSIGNED);
        return copy(newVehicleId, newDriverId, plannedPeriod, actualStart, actualEnd, TripStatus.ASSIGNED,
                statusBeforeHold, holdReason, cancellationReason, closureReason, closureEvidenceId, startOdometer,
                endOdometer, newMetadata);
    }

    /**
     * Starts the trip.
     *
     * @param startOdometerReading the reading taken as the vehicle leaves; it must not be lower than
     *        the vehicle's recorded odometer, which the caller checks
     */
    public Trip start(Instant startedAt, long startOdometerReading, RecordMetadata newMetadata) {
        TripTransitionPolicy.requireTransition(status, TripStatus.IN_PROGRESS);
        if (vehicleId == null || driverId == null) {
            throw new InvalidStateTransitionException(Map.of(
                    "aggregate", "Trip",
                    "fromStatus", status.name(),
                    "toStatus", TripStatus.IN_PROGRESS.name(),
                    "reason", "A trip cannot start without an assigned vehicle and driver"));
        }
        return copy(vehicleId, driverId, plannedPeriod, startedAt, actualEnd, TripStatus.IN_PROGRESS, null, null,
                cancellationReason, closureReason, closureEvidenceId, startOdometerReading, endOdometer,
                newMetadata);
    }

    /** Places the trip on hold, remembering what it was doing so resume can restore it. */
    public Trip hold(String reason, RecordMetadata newMetadata) {
        TripTransitionPolicy.requireTransition(status, TripStatus.ON_HOLD);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A hold reason is required");
        }
        return copy(vehicleId, driverId, plannedPeriod, actualStart, actualEnd, TripStatus.ON_HOLD, status,
                reason.strip(), cancellationReason, closureReason, closureEvidenceId, startOdometer, endOdometer,
                newMetadata);
    }

    /** Resumes a held trip to the status it held before. */
    public Trip resume(RecordMetadata newMetadata) {
        if (status != TripStatus.ON_HOLD) {
            throw InvalidStateTransitionException.of("Trip", status, TripStatus.ASSIGNED);
        }
        TripStatus restored = statusBeforeHold == null ? TripStatus.ASSIGNED : statusBeforeHold;
        TripTransitionPolicy.requireTransition(TripStatus.ON_HOLD, restored);
        return copy(vehicleId, driverId, plannedPeriod, actualStart, actualEnd, restored, null, null,
                cancellationReason, closureReason, closureEvidenceId, startOdometer, endOdometer, newMetadata);
    }

    /** Cancels the trip. A reason is mandatory; the SRS requires closure and cancellation to be explained. */
    public Trip cancel(String reason, Instant cancelledAt, RecordMetadata newMetadata) {
        TripTransitionPolicy.requireTransition(status, TripStatus.CANCELLED);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A cancellation reason is required");
        }
        return copy(vehicleId, driverId, plannedPeriod, actualStart, cancelledAt, TripStatus.CANCELLED, null, null,
                reason.strip(), closureReason, closureEvidenceId, startOdometer, endOdometer, newMetadata);
    }

    /**
     * Closes the trip.
     *
     * <p>SRS-SFL-S166-02: "A workflow cannot be closed without required evidence or closure reason."
     * Both are required here, and the end odometer must not regress.
     */
    public Trip close(String reason, UUID evidenceId, long endOdometerReading, Instant completedAt,
            RecordMetadata newMetadata) {
        TripTransitionPolicy.requireTransition(status, TripStatus.COMPLETED);
        if (reason == null || reason.isBlank()) {
            throw new ClosureEvidenceMissingException(Map.of(
                    "tripId", id.toString(),
                    "missing", "closureReason"));
        }
        if (evidenceId == null) {
            throw new ClosureEvidenceMissingException(Map.of(
                    "tripId", id.toString(),
                    "missing", "closureEvidenceId"));
        }
        if (startOdometer != null && endOdometerReading < startOdometer) {
            throw OdometerRegressionException.of(startOdometer, endOdometerReading);
        }
        return copy(vehicleId, driverId, plannedPeriod, actualStart, completedAt, TripStatus.COMPLETED, null, null,
                cancellationReason, reason.strip(), evidenceId, startOdometer, endOdometerReading, newMetadata);
    }

    /** True while the trip holds its vehicle and driver against the planned period. */
    public boolean holdsAssignment() {
        return status.holdsAssignment();
    }

    /** Distance covered, once the trip has both readings. */
    public Long distanceCovered() {
        return startOdometer == null || endOdometer == null ? null : endOdometer - startOdometer;
    }

    private Trip copy(UUID newVehicleId, UUID newDriverId, DateTimeRange newPlannedPeriod, Instant newActualStart,
            Instant newActualEnd, TripStatus newStatus, TripStatus newStatusBeforeHold, String newHoldReason,
            String newCancellationReason, String newClosureReason, UUID newClosureEvidenceId,
            Long newStartOdometer, Long newEndOdometer, RecordMetadata newMetadata) {
        return new Trip(id, tripNumber, newVehicleId, newDriverId, siteCode, purpose, origin, destination,
                operatingMode, newPlannedPeriod, newActualStart, newActualEnd, newStatus, newStatusBeforeHold,
                newHoldReason, newCancellationReason, newClosureReason, newClosureEvidenceId, newStartOdometer,
                newEndOdometer, newMetadata);
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
}
