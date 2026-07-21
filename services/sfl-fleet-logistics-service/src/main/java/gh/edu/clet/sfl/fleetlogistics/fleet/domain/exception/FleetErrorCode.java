package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

/**
 * Machine-readable fleet error codes and their user-facing messages.
 *
 * <p>Codes whose {@link #srsDefined()} flag is {@code true} carry the wording of an SRS
 * <em>Error States</em> entry verbatim; that wording is contract and must not be reworded.
 * The remaining codes are service-defined and use British English to match the SRS style.
 *
 * <p>Transport concerns stay out of the domain: the HTTP status for each code is decided by
 * {@code gh.edu.clet.sfl.fleetlogistics.fleet.api.FleetHttpStatusMapper}.
 */
public enum FleetErrorCode {

    // --- SRS-SFL-S166-01 -------------------------------------------------------------------
    FLEET_DUPLICATE_IDENTIFIER("An active record with this identifier already exists for this site.", true),
    FLEET_MISSING_SITE_SCOPE("Select a valid CLET site before saving this record.", true),
    FLEET_UNAUTHORIZED_SCOPE("You are not authorised to access this site or record.", true),

    // --- SRS-SFL-S166-02 -------------------------------------------------------------------
    FLEET_CLOSURE_EVIDENCE_MISSING("Required evidence must be attached before closure.", true),
    FLEET_SLA_BREACH("This item has breached its configured SLA and has been escalated.", true),
    FLEET_UNAUTHORIZED_APPROVAL("You do not have permission to approve this workflow transition.", true),

    // --- SRS-SFL-S166-03 -------------------------------------------------------------------
    FLEET_EXPORT_NOT_APPROVED("Evidence export requires approval and a recorded reason.", true),
    FLEET_RETENTION_CLASS_MISSING("Select a retention class before saving this evidence.", true),
    FLEET_AUDIT_CHAIN_FAILURE("Audit integrity check failed. Escalate to compliance and security.", true),

    // --- SRS-SFL-S166-04 -------------------------------------------------------------------
    FLEET_INTEGRATION_INVALID_SIGNATURE("Integration message rejected: signature verification failed.", true),
    FLEET_INTEGRATION_SCHEMA_INVALID(
            "Integration message rejected: payload does not match registered schema.", true),
    FLEET_INTEGRATION_DUPLICATE_MESSAGE("Duplicate integration message received and safely ignored.", true),

    // --- SRS-SFL-S166-05 -------------------------------------------------------------------
    FLEET_DASHBOARD_DATA_STALE("Dashboard data is older than the configured freshness threshold.", true),
    FLEET_DASHBOARD_NO_SCOPE("No site scope is assigned to your user profile.", true),
    FLEET_DASHBOARD_RESTRICTED_DRILLDOWN("You do not have permission to view the underlying record.", true),

    // --- Service-defined ------------------------------------------------------------------
    FLEET_VALIDATION_FAILED("The request could not be validated.", false),
    FLEET_RECORD_NOT_FOUND("The requested fleet record was not found.", false),
    FLEET_RECORD_VERSION_CONFLICT("This record was changed by another user. Reload the record and try again.", false),
    FLEET_INVALID_STATE_TRANSITION("This transition is not permitted from the current status.", false),
    FLEET_ARCHIVED_RECORD_IMMUTABLE(
            "Archived records cannot be edited outside an authorised restoration workflow.", false),
    FLEET_ASSIGNMENT_CONFLICT("The vehicle or driver is already assigned during the requested period.", false),
    FLEET_READINESS_BLOCKED("The vehicle is not ready for assignment. Resolve the listed readiness blockers.", false),
    FLEET_DRIVER_INELIGIBLE("The driver is not eligible for this assignment.", false),
    FLEET_ODOMETER_REGRESSION("The odometer reading is lower than the last recorded reading for this vehicle.", false),
    FLEET_INTEGRATION_SOURCE_NOT_ALLOWED("Integration message rejected: source system is not allowlisted.", false),
    FLEET_INTEGRATION_NOT_CONFIGURED("The required integration is not configured for this environment.", false),
    FLEET_IDEMPOTENCY_KEY_REQUIRED("An Idempotency-Key header is required for this request.", false),
    FLEET_IDEMPOTENCY_KEY_CONFLICT("This Idempotency-Key was already used with a different request payload.", false);

    private final String message;
    private final boolean srsDefined;

    FleetErrorCode(String message, boolean srsDefined) {
        this.message = message;
        this.srsDefined = srsDefined;
    }

    /** The user-facing message. For SRS-defined codes this is the SRS wording, verbatim. */
    public String message() {
        return message;
    }

    /** {@code true} when the message text is fixed by an SRS <em>Error States</em> entry. */
    public boolean srsDefined() {
        return srsDefined;
    }

    /** The machine-readable code carried in {@code ApiError.code}. */
    public String code() {
        return name();
    }
}
