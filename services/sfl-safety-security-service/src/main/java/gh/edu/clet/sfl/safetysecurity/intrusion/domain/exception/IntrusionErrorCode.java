package gh.edu.clet.sfl.safetysecurity.intrusion.domain.exception;

/** S162 error catalog, following S160a's {@code AccessControlErrorCode} shape. */
public enum IntrusionErrorCode {

    INTRUSION_UNAUTHORIZED_SCOPE(403, "You are not authorised to access this site or record."),

    // Integration ingestion (S162-01)
    INTRUSION_SOURCE_NOT_ALLOWED(403, "This source system is not allowlisted for intrusion integration."),
    INTRUSION_INVALID_SIGNATURE(401, "The inbound message failed signature or timestamp validation."),
    INTRUSION_SCHEMA_VALIDATION_FAILED(400, "The inbound message failed schema validation."),
    INTRUSION_DUPLICATE_MESSAGE(409, "This message was already processed and has been ignored."),

    // Zone arming (S162-04)
    INTRUSION_ZONE_DISARM_NOT_AUTHORISED(403,
            "You are not permitted to authorise this disarm, or it lacks a reason or expiry."),
    INTRUSION_ZONE_DISARM_ALREADY_CLOSED(409, "This disarm override has already expired or been revoked."),

    // Alarm lifecycle (S162-02/03)
    INTRUSION_ALARM_ALREADY_CLOSED(409, "This alarm has already been resolved."),
    INTRUSION_ALARM_NOT_ACKNOWLEDGED(409, "This alarm must be acknowledged before it can be linked or resolved."),

    // Generic operational codes
    INTRUSION_VALIDATION_FAILED(400, "The request failed validation."),
    INTRUSION_RECORD_NOT_FOUND(404, "The requested intrusion record was not found."),
    INTRUSION_INVALID_STATE_TRANSITION(409, "The requested transition is not allowed from the current state."),
    INTRUSION_RECORD_VERSION_CONFLICT(409, "The record was modified concurrently; reload and retry.");

    private final int httpStatus;
    private final String message;

    IntrusionErrorCode(int httpStatus, String message) {
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
