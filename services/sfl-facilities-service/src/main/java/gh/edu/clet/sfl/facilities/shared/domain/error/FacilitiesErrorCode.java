package gh.edu.clet.sfl.facilities.shared.domain.error;

/**
 * The machine-readable error codes for S152 and S153, one per *Error State* named in the SRS.
 *
 * <p>The SRS states its error conditions as prose — "Duplicate Identifier - 'An active record with
 * this identifier already exists for this site.'" — which is testable only if the wire carries
 * something more stable than that sentence. Each constant below is that stable thing, and
 * {@link #defaultMessage()} carries the SRS wording verbatim so the two cannot drift apart.
 *
 * <p>Codes are the contract. A message may be reworded for a UI; a code may not change.
 */
public enum FacilitiesErrorCode {

    // SRS-SFL-S152-01 — operational records
    DUPLICATE_IDENTIFIER("An active record with this identifier already exists for this site."),
    MISSING_SITE_SCOPE("Select a valid CLET site before saving this record."),
    UNAUTHORIZED_SCOPE("You are not authorised to access this site or record."),

    // SRS-SFL-S152-02 — workflow
    CLOSURE_EVIDENCE_MISSING("Required evidence must be attached before closure."),
    UNAUTHORIZED_APPROVAL("You do not have permission to approve this workflow transition."),
    INVALID_STATE_TRANSITION("This record cannot move to the requested state from its current state."),

    // SRS-SFL-S152-03 and S153-03 — evidence and audit
    AUDIT_CHAIN_FAILURE("Audit integrity check failed. Escalate to compliance and security."),
    RETENTION_CLASS_MISSING("Select a retention class before saving this evidence."),
    EXPORT_NOT_APPROVED("Evidence export requires approval and a recorded reason."),

    // SRS-SFL-S153-02 — CMMS workflow. SLA_BREACH is not a refusal: it is the state an item is put
    // into by the scheduled evaluator, and it is here so the escalation event and any UI carry the
    // SRS's wording rather than each inventing their own.
    SLA_BREACH("This item has breached its configured SLA and has been escalated."),

    // SRS-SFL-S152-04 — integration
    DUPLICATE_MESSAGE("Duplicate integration message received and safely ignored."),
    IDEMPOTENCY_KEY_CONFLICT("This idempotency key was already used with a different request payload."),

    // SRS-SFL-S152-05 — dashboards
    DATA_STALE("Dashboard data is older than the configured freshness threshold."),
    NO_SCOPE("No site scope is assigned to your user profile."),
    RESTRICTED_DRILLDOWN("You do not have permission to view the underlying record."),

    // Cross-cutting
    RECORD_NOT_FOUND("The requested record does not exist."),
    INVALID_PARENT_REFERENCE("The parent record referenced by this request does not exist."),
    VALIDATION_FAILED("The request failed validation."),
    VERSION_CONFLICT("This record was changed by someone else. Reload and try again."),
    READINESS_BLOCKED("This space cannot be marked ready while critical blockers remain open."),
    READINESS_LOCKED("This space is locked for examination use and cannot be changed without an override."),
    OPERATING_MODE_TRANSITION_INVALID("The site is already in the requested operating mode.");

    private final String defaultMessage;

    FacilitiesErrorCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    /** The SRS wording for this error state. */
    public String defaultMessage() {
        return defaultMessage;
    }
}
