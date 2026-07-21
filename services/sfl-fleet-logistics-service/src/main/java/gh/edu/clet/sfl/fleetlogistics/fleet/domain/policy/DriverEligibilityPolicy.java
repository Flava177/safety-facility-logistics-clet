package gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocument;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverLifecycleStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverProfileReference;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EligibilityAssessment;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ReadinessBlocker;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ReadinessBlockerCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleCategory;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Decides whether a driver may be assigned, and says exactly why not (SRS-SFL-S166-05 "driver
 * eligibility blockers").
 *
 * <p>Every check produces a blocker rather than a bare boolean, so a dispatcher who cannot use a
 * driver is told which licence expired, on what date, and whether it merely expires soon.
 *
 * <p>Warning-level findings produce {@code CONDITIONAL} eligibility: the assignment may go ahead, but
 * the operator has been told. Blocking findings produce {@code INELIGIBLE}.
 */
public final class DriverEligibilityPolicy {

    private DriverEligibilityPolicy() {
    }

    /**
     * Assesses a driver.
     *
     * @param vehicleCategory the category they would drive, or {@code null} for a general assessment
     * @param periodEnd the end of the proposed assignment, or {@code null} to assess at {@code now};
     *        checking against the end of the period is what stops a licence expiring mid-trip
     * @param warningWindow how far ahead an expiry is reported as a warning
     * @param requiredSite the site the assignment is for, or {@code null} to skip the scope check
     */
    public static EligibilityAssessment assess(DriverProfileReference driver, VehicleCategory vehicleCategory,
            Instant now, Instant periodEnd, Duration warningWindow, SiteCode requiredSite) {
        List<ReadinessBlocker> blockers = new ArrayList<>();
        Instant effectiveEnd = periodEnd == null ? now : periodEnd;

        addLifecycleBlockers(driver, blockers);
        addLicenceBlockers(driver, vehicleCategory, now, effectiveEnd, warningWindow, blockers);
        addMedicalClearanceBlockers(driver, now, effectiveEnd, warningWindow, blockers);
        addSiteBlockers(driver, requiredSite, blockers);

        return EligibilityAssessment.from(driver.id(), driver.lifecycleStatus(), blockers, now, vehicleCategory);
    }

    private static void addLifecycleBlockers(DriverProfileReference driver, List<ReadinessBlocker> blockers) {
        switch (driver.lifecycleStatus()) {
            case SUSPENDED -> blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.DRIVER_SUSPENDED,
                    driver.suspensionReason() == null ? null : "Reason: " + driver.suspensionReason() + ".",
                    Map.of("driverId", driver.id().toString(),
                            "suspensionReason", String.valueOf(driver.suspensionReason()))));
            case INACTIVE, ARCHIVED -> blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.DRIVER_NOT_ACTIVE,
                    Map.of("driverId", driver.id().toString(),
                            "lifecycleStatus", driver.lifecycleStatus().name())));
            case ACTIVE -> {
                // No lifecycle blocker.
            }
            default -> throw new IllegalStateException("Unhandled driver lifecycle status: "
                    + driver.lifecycleStatus());
        }
    }

    private static void addLicenceBlockers(DriverProfileReference driver, VehicleCategory vehicleCategory,
            Instant now, Instant effectiveEnd, Duration warningWindow, List<ReadinessBlocker> blockers) {
        if (driver.licence().isExpiredAt(now)) {
            blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.DRIVER_LICENCE_EXPIRED,
                    "Expired on " + driver.licence().expiresOn() + ".",
                    Map.of("driverId", driver.id().toString(),
                            "licenceExpiresOn", driver.licence().expiresOn().toString())));
        } else if (driver.licence().expiresBefore(effectiveEnd)) {
            // Valid today but not for the whole trip: still a hard block, and the reason says so.
            blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.DRIVER_LICENCE_EXPIRED,
                    "The licence expires on " + driver.licence().expiresOn()
                            + ", before the end of the requested period.",
                    Map.of("driverId", driver.id().toString(),
                            "licenceExpiresOn", driver.licence().expiresOn().toString())));
        } else if (driver.licence().daysUntilExpiry(now) <= warningWindow.toDays()) {
            blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.DRIVER_LICENCE_EXPIRING,
                    "Expires on " + driver.licence().expiresOn() + ".",
                    Map.of("driverId", driver.id().toString(),
                            "licenceExpiresOn", driver.licence().expiresOn().toString(),
                            "daysRemaining", driver.licence().daysUntilExpiry(now))));
        }

        if (vehicleCategory != null && !driver.licence().covers(vehicleCategory)) {
            blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.DRIVER_LICENCE_CLASS_MISMATCH,
                    "Licence class " + driver.licence().licenceClass() + " does not cover "
                            + vehicleCategory + ".",
                    Map.of("driverId", driver.id().toString(),
                            "licenceClass", driver.licence().licenceClass().name(),
                            "vehicleCategory", vehicleCategory.name())));
        }
    }

    private static void addMedicalClearanceBlockers(DriverProfileReference driver, Instant now,
            Instant effectiveEnd, Duration warningWindow, List<ReadinessBlocker> blockers) {
        LocalDate clearanceExpiry = driver.medicalClearanceExpiresOn();
        if (clearanceExpiry == null) {
            // Medical clearance is not required for every driver class; absence is not a finding.
            return;
        }
        LocalDate today = LocalDate.ofInstant(now, ComplianceDocument.OPERATING_ZONE);
        LocalDate periodEndDate = LocalDate.ofInstant(effectiveEnd, ComplianceDocument.OPERATING_ZONE);

        if (clearanceExpiry.isBefore(today) || clearanceExpiry.isBefore(periodEndDate)) {
            blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.DRIVER_MEDICAL_CLEARANCE_EXPIRED,
                    "Medical clearance expires on " + clearanceExpiry + ".",
                    Map.of("driverId", driver.id().toString(),
                            "medicalClearanceExpiresOn", clearanceExpiry.toString())));
        } else if (ChronoUnit.DAYS.between(today, clearanceExpiry) <= warningWindow.toDays()) {
            blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.DRIVER_MEDICAL_CLEARANCE_EXPIRING,
                    "Expires on " + clearanceExpiry + ".",
                    Map.of("driverId", driver.id().toString(),
                            "medicalClearanceExpiresOn", clearanceExpiry.toString(),
                            "daysRemaining", ChronoUnit.DAYS.between(today, clearanceExpiry))));
        }
    }

    private static void addSiteBlockers(DriverProfileReference driver, SiteCode requiredSite,
            List<ReadinessBlocker> blockers) {
        if (requiredSite != null && !driver.siteCode().equals(requiredSite)) {
            blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.DRIVER_SITE_RESTRICTION,
                    "The driver is registered at " + driver.siteCode() + ".",
                    Map.of("driverId", driver.id().toString(),
                            "driverSiteCode", driver.siteCode().value(),
                            "requestedSiteCode", requiredSite.value())));
        }
    }

    /** Convenience for the scheduled sweep, which reassesses without a specific vehicle or period. */
    public static EligibilityAssessment assessGeneral(DriverProfileReference driver, Instant now,
            Duration warningWindow) {
        return assess(driver, null, now, null, warningWindow, null);
    }

    /** Whether the assessed status differs from the one currently stored on the profile. */
    public static boolean hasChanged(DriverProfileReference driver, EligibilityAssessment assessment) {
        return driver.eligibilityStatus() != assessment.status();
    }

    /** Guards against an unhandled lifecycle value silently passing as eligible. */
    static void requireKnownLifecycle(DriverLifecycleStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("driver lifecycle status is required");
        }
    }
}
