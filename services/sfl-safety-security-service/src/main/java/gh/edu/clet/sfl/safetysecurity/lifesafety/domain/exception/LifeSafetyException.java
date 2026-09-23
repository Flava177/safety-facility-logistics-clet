package gh.edu.clet.sfl.safetysecurity.lifesafety.domain.exception;

import java.util.Map;
import java.util.UUID;

/** The single domain/application exception for S162a, framework-free - mirrors {@code IncidentException}. */
public class LifeSafetyException extends RuntimeException {

    private final LifeSafetyErrorCode errorCode;
    private final transient Map<String, Object> details;

    public LifeSafetyException(LifeSafetyErrorCode errorCode, Map<String, Object> details) {
        super(errorCode.message());
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public LifeSafetyException(LifeSafetyErrorCode errorCode) {
        this(errorCode, Map.of());
    }

    public LifeSafetyErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> details() {
        return details;
    }

    public static LifeSafetyException notFound(String type, UUID id) {
        return new LifeSafetyException(LifeSafetyErrorCode.LIFESAFETY_RECORD_NOT_FOUND,
                Map.of("resourceType", type, "resourceId", id == null ? "" : id.toString()));
    }

    public static LifeSafetyException unauthorizedScope(String site, String resource, String id) {
        return new LifeSafetyException(LifeSafetyErrorCode.LIFESAFETY_UNAUTHORIZED_SCOPE,
                Map.of("siteCode", site == null ? "" : site, "resourceType", resource,
                        "resourceId", id == null ? "" : id));
    }
}
