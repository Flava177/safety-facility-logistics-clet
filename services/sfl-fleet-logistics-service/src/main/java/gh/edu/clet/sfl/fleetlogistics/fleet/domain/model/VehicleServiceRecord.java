package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * One completed maintenance event and the next-due schedule it sets (SRS-SFL-S166-01 "service status
 * and service history").
 *
 * <p>The next service falls due on whichever of the date or the odometer target arrives first, which
 * is how fleet maintenance actually works: a rarely used minibus is still due annually, and a heavily
 * used pickup is due at 10 000 km regardless of the calendar.
 */
public record VehicleServiceRecord(
        UUID id,
        UUID vehicleId,
        SiteCode siteCode,
        ServiceType serviceType,
        LocalDate performedOn,
        long odometerAtService,
        LocalDate nextDueOn,
        Long nextDueOdometer,
        String providerReference,
        String workSummary,
        ServiceOutcome outcome,
        UUID evidenceId,
        RecordMetadata metadata) {

    public VehicleServiceRecord {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(vehicleId, "vehicleId is required");
        Objects.requireNonNull(siteCode, "siteCode is required");
        Objects.requireNonNull(serviceType, "serviceType is required");
        Objects.requireNonNull(performedOn, "performedOn is required");
        Objects.requireNonNull(outcome, "outcome is required");
        Objects.requireNonNull(metadata, "metadata is required");
        workSummary = requireText(workSummary, "workSummary", 2000);
        providerReference = trimToNull(providerReference);
        if (odometerAtService < 0) {
            throw new IllegalArgumentException("odometerAtService cannot be negative");
        }
        if (nextDueOn != null && nextDueOn.isBefore(performedOn)) {
            throw new IllegalArgumentException("nextDueOn cannot precede performedOn");
        }
        if (nextDueOdometer != null && nextDueOdometer < odometerAtService) {
            throw new IllegalArgumentException("nextDueOdometer cannot be lower than odometerAtService");
        }
    }

    public static VehicleServiceRecord record(UUID id, UUID vehicleId, SiteCode siteCode, ServiceType serviceType,
            LocalDate performedOn, long odometerAtService, LocalDate nextDueOn, Long nextDueOdometer,
            String providerReference, String workSummary, ServiceOutcome outcome, UUID evidenceId,
            RecordMetadata metadata) {
        return new VehicleServiceRecord(id, vehicleId, siteCode, serviceType, performedOn, odometerAtService,
                nextDueOn, nextDueOdometer, providerReference, workSummary, outcome, evidenceId, metadata);
    }

    /**
     * Derives the vehicle's service status from this record at {@code now}.
     *
     * <p>A service that did not complete leaves the vehicle out of service: reporting IN_SERVICE for
     * unfinished work would put an unsafe vehicle back on the readiness list.
     */
    public VehicleServiceStatus deriveStatus(Instant now, long currentOdometer, java.time.Duration warningWindow) {
        if (!outcome.returnsVehicleToService()) {
            return VehicleServiceStatus.OUT_OF_SERVICE;
        }
        LocalDate today = LocalDate.ofInstant(now, ComplianceDocument.OPERATING_ZONE);

        boolean overdueByDate = nextDueOn != null && nextDueOn.isBefore(today);
        boolean overdueByOdometer = nextDueOdometer != null && currentOdometer >= nextDueOdometer;
        if (overdueByDate || overdueByOdometer) {
            return VehicleServiceStatus.OVERDUE;
        }

        boolean dueByDate = nextDueOn != null
                && !nextDueOn.isAfter(today.plusDays(warningWindow.toDays()));
        // Treat the last 5% of the service interval as "due" so a warning appears before the threshold.
        boolean dueByOdometer = nextDueOdometer != null
                && currentOdometer >= nextDueOdometer - odometerWarningMargin();
        if (dueByDate || dueByOdometer) {
            return VehicleServiceStatus.DUE;
        }
        return VehicleServiceStatus.IN_SERVICE;
    }

    private long odometerWarningMargin() {
        if (nextDueOdometer == null) {
            return 0L;
        }
        long interval = nextDueOdometer - odometerAtService;
        return Math.max(interval / 20, 0L);
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

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
