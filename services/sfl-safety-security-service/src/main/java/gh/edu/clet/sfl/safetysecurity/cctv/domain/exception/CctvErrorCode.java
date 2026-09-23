package gh.edu.clet.sfl.safetysecurity.cctv.domain.exception;

/** S161 error catalog, following S160a's {@code AccessControlErrorCode} shape. */
public enum CctvErrorCode {

    CCTV_UNAUTHORIZED_SCOPE(403, "You are not authorised to access this site or record."),

    // Integration ingestion (S161-01/04)
    CCTV_SOURCE_NOT_ALLOWED(403, "This source system is not allowlisted for CCTV integration."),
    CCTV_INVALID_SIGNATURE(401, "The inbound message failed signature or timestamp validation."),
    CCTV_SCHEMA_VALIDATION_FAILED(400, "The inbound message failed schema validation."),
    CCTV_DUPLICATE_MESSAGE(409, "This message was already processed and has been ignored."),

    // Evidence request and retrieval (S161-02/03/04)
    CCTV_EVIDENCE_REQUEST_NOT_APPROVED(403,
            "Footage cannot be retrieved: this evidence request is not approved by an authorised security role."),
    CCTV_EVIDENCE_REQUEST_ALREADY_DECIDED(409, "This evidence request has already been approved or rejected."),
    CCTV_LIVE_VIEW_NOT_PERMITTED(403, "You are not permitted to view this camera or zone."),

    // Retention and disclosure (S161-05)
    CCTV_DISCLOSURE_NOT_AUTHORISED(403,
            "This footage cannot be disclosed: the disclosure is not approved or lacks a recorded purpose and "
                    + "recipient."),
    CCTV_DISCLOSURE_ALREADY_DECIDED(409, "This disclosure has already been decided."),

    // Generic operational codes
    CCTV_VALIDATION_FAILED(400, "The request failed validation."),
    CCTV_RECORD_NOT_FOUND(404, "The requested CCTV record was not found."),
    CCTV_INVALID_STATE_TRANSITION(409, "The requested transition is not allowed from the current state."),
    CCTV_RECORD_VERSION_CONFLICT(409, "The record was modified concurrently; reload and retry.");

    private final int httpStatus;
    private final String message;

    CctvErrorCode(int httpStatus, String message) {
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
