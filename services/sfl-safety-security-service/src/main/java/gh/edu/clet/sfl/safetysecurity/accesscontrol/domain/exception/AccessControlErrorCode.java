package gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.exception;

/** S160a error catalog, following S160/S163's {@code VisitorErrorCode}/{@code IncidentErrorCode} shape. */
public enum AccessControlErrorCode {

    ACCESS_CONTROL_UNAUTHORIZED_SCOPE(403, "You are not authorised to access this site or record."),

    // Integration ingestion (S160a-01)
    ACCESS_SOURCE_NOT_ALLOWED(403, "This source system is not allowlisted for access-control integration."),
    ACCESS_INVALID_SIGNATURE(401, "The inbound message failed signature or timestamp validation."),
    ACCESS_SCHEMA_VALIDATION_FAILED(400, "The inbound message failed schema validation."),
    ACCESS_DUPLICATE_MESSAGE(409, "This message was already processed and has been ignored."),

    // Provisioning (S160a-02)
    ACCESS_PROVISIONING_MANUAL_GRANT_REQUIRES_REASON(400,
            "A manual access grant requires a reason, an approver and an expiry."),

    // Overrides (S160a-03)
    ACCESS_OVERRIDE_NOT_AUTHORISED(403,
            "You are not permitted to authorise this access override, or it lacks a reason or expiry."),
    ACCESS_OVERRIDE_ALREADY_CLOSED(409, "This override has already expired or been revoked."),

    // Zones / door groups (S160a-05)
    ACCESS_ZONE_RULE_TOO_BROAD(422,
            "This access rule grants unusually broad access and has been flagged for review."),

    // Generic operational codes
    ACCESS_CONTROL_VALIDATION_FAILED(400, "The request failed validation."),
    ACCESS_CONTROL_RECORD_NOT_FOUND(404, "The requested access-control record was not found."),
    ACCESS_CONTROL_INVALID_STATE_TRANSITION(409, "The requested transition is not allowed from the current state."),
    ACCESS_CONTROL_RECORD_VERSION_CONFLICT(409, "The record was modified concurrently; reload and retry.");

    private final int httpStatus;
    private final String message;

    AccessControlErrorCode(int httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String code() {
        return name();
    }

    public String message() {
        return message;
    }
}
