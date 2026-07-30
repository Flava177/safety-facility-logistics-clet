package gh.edu.clet.sfl.facilities.shared.domain.error;

import java.util.UUID;

/**
 * The base of every domain-signalled failure in this service.
 *
 * <p>Carrying a {@link FacilitiesErrorCode} rather than only a message is what lets the exception
 * handler map a failure to its HTTP status and its SRS error state without inspecting the message
 * text. Everything below is a named subclass rather than this class thrown with a code, so a call
 * site reads as the rule it is enforcing.
 */
public class FacilitiesException extends RuntimeException {

    private final FacilitiesErrorCode code;

    public FacilitiesException(FacilitiesErrorCode code) {
        this(code, code.defaultMessage());
    }

    public FacilitiesException(FacilitiesErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public FacilitiesErrorCode code() {
        return code;
    }

    /**
     * A record that does not exist.
     *
     * <p>The identifier is deliberately included: this is not a permission boundary — an actor who
     * cannot see a site is refused with {@link UnauthorizedScopeException} before the lookup runs.
     */
    public static class RecordNotFoundException extends FacilitiesException {
        public RecordNotFoundException(String type, Object id) {
            super(FacilitiesErrorCode.RECORD_NOT_FOUND, type + " was not found: " + id);
        }
    }

    /** A parent referenced by a create command does not exist — a floor whose building is unknown. */
    public static class InvalidParentReferenceException extends FacilitiesException {
        public InvalidParentReferenceException(String parentType, Object id) {
            super(FacilitiesErrorCode.INVALID_PARENT_REFERENCE,
                    parentType + " referenced by this request does not exist: " + id);
        }
    }

    /** An active record already uses this identifier within the site. SRS-SFL-S152-01. */
    public static class DuplicateIdentifierException extends FacilitiesException {
        public DuplicateIdentifierException(String type, String identifier, String siteCode) {
            super(FacilitiesErrorCode.DUPLICATE_IDENTIFIER,
                    "An active " + type + " with identifier '" + identifier + "' already exists for site "
                            + siteCode + ".");
        }
    }

    /** The command carried no site scope, or one that does not resolve. SRS-SFL-S152-01. */
    public static class MissingSiteScopeException extends FacilitiesException {
        public MissingSiteScopeException() {
            super(FacilitiesErrorCode.MISSING_SITE_SCOPE);
        }
    }

    /** The actor may not act on this site or record. SRS-SFL-S152-01. */
    public static class UnauthorizedScopeException extends FacilitiesException {
        public UnauthorizedScopeException(String detail) {
            super(FacilitiesErrorCode.UNAUTHORIZED_SCOPE, detail);
        }
    }

    /** The actor holds no site scope at all. SRS-SFL-S152-05. */
    public static class NoScopeException extends FacilitiesException {
        public NoScopeException() {
            super(FacilitiesErrorCode.NO_SCOPE);
        }
    }

    /** A lifecycle or status change the state machine does not allow. */
    public static class InvalidStateTransitionException extends FacilitiesException {
        public InvalidStateTransitionException(String detail) {
            super(FacilitiesErrorCode.INVALID_STATE_TRANSITION, detail);
        }
    }

    /** The caller's version is behind the stored one. SRS-SFL-S152-01 ("version"). */
    public static class VersionConflictException extends FacilitiesException {
        public VersionConflictException(String type, Object id, long expected, long actual) {
            super(FacilitiesErrorCode.VERSION_CONFLICT,
                    type + " " + id + " has moved on: expected version " + expected + " but found " + actual + ".");
        }
    }

    /** The same idempotency key arrived with a different payload. SRS-SFL-S152-04. */
    public static class IdempotencyKeyConflictException extends FacilitiesException {
        public IdempotencyKeyConflictException(String operation, String key) {
            super(FacilitiesErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                    "Idempotency key '" + key + "' was already used for operation '" + operation
                            + "' with a different request payload.");
        }
    }

    /** READY refused while a critical blocker is open — the rule S152 readiness turns on. */
    public static class ReadinessBlockedException extends FacilitiesException {
        public ReadinessBlockedException(int openCriticalBlockers) {
            super(FacilitiesErrorCode.READINESS_BLOCKED,
                    "This space cannot be marked ready while " + openCriticalBlockers
                            + " critical blocker(s) remain open.");
        }
    }

    /** A change refused because the space is locked for examination use. NFR 23.3. */
    public static class ReadinessLockedException extends FacilitiesException {
        public ReadinessLockedException(UUID roomId) {
            super(FacilitiesErrorCode.READINESS_LOCKED,
                    "Space " + roomId + " is locked for examination use and cannot be changed without an override.");
        }
    }

    /** A no-op or disallowed operating-mode change. NFR 23.3. */
    public static class OperatingModeTransitionException extends FacilitiesException {
        public OperatingModeTransitionException(String detail) {
            super(FacilitiesErrorCode.OPERATING_MODE_TRANSITION_INVALID, detail);
        }
    }

    /** The audit chain replayed as broken. SRS-SFL-S152-03. */
    public static class AuditChainFailureException extends FacilitiesException {
        public AuditChainFailureException(String detail) {
            super(FacilitiesErrorCode.AUDIT_CHAIN_FAILURE, detail);
        }
    }

    /** A field-level validation failure raised from the domain rather than from Bean Validation. */
    public static class ValidationFailedException extends FacilitiesException {
        public ValidationFailedException(String detail) {
            super(FacilitiesErrorCode.VALIDATION_FAILED, detail);
        }
    }
}
