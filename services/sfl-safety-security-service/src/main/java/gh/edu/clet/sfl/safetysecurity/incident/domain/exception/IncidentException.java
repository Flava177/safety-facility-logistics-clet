package gh.edu.clet.sfl.safetysecurity.incident.domain.exception;

import java.util.Map;
import java.util.UUID;

/**
 * The single domain/application exception for S163. It carries a typed {@link IncidentErrorCode}
 * (which owns the user-facing message and the HTTP status) plus optional structured details.
 * Framework-free.
 */
public class IncidentException extends RuntimeException {

    private final IncidentErrorCode errorCode;
    private final transient Map<String, Object> details;

    public IncidentException(IncidentErrorCode errorCode, Map<String, Object> details) {
        super(errorCode.message());
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public IncidentException(IncidentErrorCode errorCode) {
        this(errorCode, Map.of());
    }

    public IncidentErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> details() {
        return details;
    }

    public static IncidentException notFound(String type, UUID id) {
        return new IncidentException(IncidentErrorCode.INCIDENT_RECORD_NOT_FOUND,
                Map.of("resourceType", type, "resourceId", id == null ? "" : id.toString()));
    }

    public static IncidentException unauthorizedScope(String site, String resource, String id) {
        return new IncidentException(IncidentErrorCode.INCIDENT_UNAUTHORIZED_SCOPE,
                Map.of("siteCode", site == null ? "" : site, "resourceType", resource,
                        "resourceId", id == null ? "" : id));
    }
}
