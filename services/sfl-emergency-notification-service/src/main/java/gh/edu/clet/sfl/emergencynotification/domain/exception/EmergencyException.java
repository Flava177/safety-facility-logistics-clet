package gh.edu.clet.sfl.emergencynotification.domain.exception;

import java.util.Map;
import java.util.UUID;

/**
 * The single domain/application exception for S174. It carries a typed {@link EmergencyErrorCode} (which
 * owns the SRS user-facing message and the HTTP status) plus optional structured details. Framework-free.
 */
public class EmergencyException extends RuntimeException {

    private final EmergencyErrorCode errorCode;
    private final transient Map<String, Object> details;

    public EmergencyException(EmergencyErrorCode errorCode, Map<String, Object> details) {
        super(errorCode.message());
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public EmergencyException(EmergencyErrorCode errorCode) {
        this(errorCode, Map.of());
    }

    public EmergencyErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> details() {
        return details;
    }

    public static EmergencyException of(EmergencyErrorCode code) {
        return new EmergencyException(code);
    }

    public static EmergencyException notFound(String type, UUID id) {
        return new EmergencyException(EmergencyErrorCode.EMERGENCY_RECORD_NOT_FOUND,
                Map.of("resourceType", type, "resourceId", id == null ? "" : id.toString()));
    }

    public static EmergencyException unauthorizedScope(String site, String resource, String id) {
        return new EmergencyException(EmergencyErrorCode.EMERGENCY_UNAUTHORIZED_SCOPE,
                Map.of("siteCode", site == null ? "" : site, "resourceType", resource,
                        "resourceId", id == null ? "" : id));
    }
}
