package gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.exception;

import java.util.Map;
import java.util.UUID;

/** The single domain/application exception for S160a. Framework-free, mirrors {@code VisitorException}. */
public class AccessControlException extends RuntimeException {

    private final AccessControlErrorCode errorCode;
    private final transient Map<String, Object> details;

    public AccessControlException(AccessControlErrorCode errorCode, Map<String, Object> details) {
        super(errorCode.message());
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public AccessControlException(AccessControlErrorCode errorCode) {
        this(errorCode, Map.of());
    }

    public AccessControlErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> details() {
        return details;
    }

    public static AccessControlException of(AccessControlErrorCode code) {
        return new AccessControlException(code);
    }

    public static AccessControlException notFound(String type, UUID id) {
        return new AccessControlException(AccessControlErrorCode.ACCESS_CONTROL_RECORD_NOT_FOUND,
                Map.of("resourceType", type, "resourceId", id == null ? "" : id.toString()));
    }

    public static AccessControlException unauthorizedScope(String site, String resource, String id) {
        return new AccessControlException(AccessControlErrorCode.ACCESS_CONTROL_UNAUTHORIZED_SCOPE,
                Map.of("siteCode", site == null ? "" : site, "resourceType", resource,
                        "resourceId", id == null ? "" : id));
    }
}
