package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

/**
 * The machine-readable reasons a vehicle or driver is not ready.
 *
 * <p>The brief is explicit that readiness must "return readiness decisions with machine-readable
 * blocker codes and human-readable explanations", so each code carries both. Callers switch on the
 * code; people read the message.
 */
public enum ReadinessBlockerCode {

    // --- Vehicle record state ----------------------------------------------------------
    VEHICLE_NOT_ACTIVE(BlockerSeverity.BLOCKING,
            "The vehicle is not active in the fleet register."),
    VEHICLE_SUSPENDED(BlockerSeverity.BLOCKING,
            "The vehicle is suspended and cannot be assigned."),
    VEHICLE_ARCHIVED(BlockerSeverity.BLOCKING,
            "The vehicle is archived and is no longer part of the operational fleet."),

    // --- Service ------------------------------------------------------------------------
    VEHICLE_OUT_OF_SERVICE(BlockerSeverity.BLOCKING,
            "The vehicle is out of service."),
    SERVICE_OVERDUE(BlockerSeverity.BLOCKING,
            "Scheduled service is overdue."),
    SERVICE_DUE_SOON(BlockerSeverity.WARNING,
            "Scheduled service is due shortly."),

    // --- Compliance ---------------------------------------------------------------------
    COMPLIANCE_DOCUMENT_MISSING(BlockerSeverity.BLOCKING,
            "A mandatory compliance document is missing."),
    COMPLIANCE_DOCUMENT_EXPIRED(BlockerSeverity.BLOCKING,
            "A compliance document has expired."),
    COMPLIANCE_DOCUMENT_EXPIRING(BlockerSeverity.WARNING,
            "A compliance document expires shortly."),

    // --- Inspection and defects ---------------------------------------------------------
    MANDATORY_INSPECTION_MISSING(BlockerSeverity.BLOCKING,
            "No valid inspection has been recorded within the configured window."),
    INSPECTION_FAILED(BlockerSeverity.BLOCKING,
            "The most recent inspection failed."),
    OPEN_CRITICAL_DEFECT(BlockerSeverity.BLOCKING,
            "An unresolved critical defect is recorded against the vehicle."),

    // --- Assignment conflicts -----------------------------------------------------------
    VEHICLE_ASSIGNMENT_CONFLICT(BlockerSeverity.BLOCKING,
            "The vehicle is already assigned during the requested period."),
    DRIVER_ASSIGNMENT_CONFLICT(BlockerSeverity.BLOCKING,
            "The driver is already assigned during the requested period."),

    // --- Driver --------------------------------------------------------------------------
    DRIVER_MISSING(BlockerSeverity.BLOCKING,
            "No driver has been nominated for this period."),
    DRIVER_INELIGIBLE(BlockerSeverity.BLOCKING,
            "The nominated driver is not eligible."),
    DRIVER_NOT_ACTIVE(BlockerSeverity.BLOCKING,
            "The driver profile is not active."),
    DRIVER_SUSPENDED(BlockerSeverity.BLOCKING,
            "The driver is suspended."),
    DRIVER_LICENCE_EXPIRED(BlockerSeverity.BLOCKING,
            "The driver's licence has expired."),
    DRIVER_LICENCE_EXPIRING(BlockerSeverity.WARNING,
            "The driver's licence expires shortly."),
    DRIVER_LICENCE_CLASS_MISMATCH(BlockerSeverity.BLOCKING,
            "The driver's licence class does not cover this vehicle category."),
    DRIVER_MEDICAL_CLEARANCE_EXPIRED(BlockerSeverity.BLOCKING,
            "The driver's medical clearance has expired."),
    DRIVER_MEDICAL_CLEARANCE_EXPIRING(BlockerSeverity.WARNING,
            "The driver's medical clearance expires shortly."),

    // --- Scope and restricted use ---------------------------------------------------------
    SITE_RESTRICTION(BlockerSeverity.BLOCKING,
            "The vehicle belongs to a different site and no authorised transfer exists."),
    DRIVER_SITE_RESTRICTION(BlockerSeverity.BLOCKING,
            "The driver belongs to a different site and no authorised transfer exists."),
    OPERATING_MODE_RESTRICTION(BlockerSeverity.BLOCKING,
            "The vehicle is not permitted to operate in the requested operating mode."),
    EMERGENCY_ONLY_RESTRICTION(BlockerSeverity.BLOCKING,
            "The vehicle is reserved for emergency use only."),

    // --- Evidence and data quality ---------------------------------------------------------
    MISSING_REQUIRED_EVIDENCE(BlockerSeverity.BLOCKING,
            "Required evidence has not been attached."),
    ODOMETER_PROVENANCE_STALE(BlockerSeverity.WARNING,
            "The last odometer reading is older than the configured staleness threshold.");

    private final BlockerSeverity severity;
    private final String message;

    ReadinessBlockerCode(BlockerSeverity severity, String message) {
        this.severity = severity;
        this.message = message;
    }

    public BlockerSeverity severity() {
        return severity;
    }

    public String message() {
        return message;
    }

    public boolean isBlocking() {
        return severity == BlockerSeverity.BLOCKING;
    }
}
