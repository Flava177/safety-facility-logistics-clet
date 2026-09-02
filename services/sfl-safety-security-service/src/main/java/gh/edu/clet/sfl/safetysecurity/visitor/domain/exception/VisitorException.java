package gh.edu.clet.sfl.safetysecurity.visitor.domain.exception;

import java.util.Map;
import java.util.UUID;

/**
 * The single domain/application exception for S160. It carries a typed {@link VisitorErrorCode}
 * (which owns the user-facing message and the HTTP status) plus optional structured details.
 * Framework-free.
 */
public class VisitorException extends RuntimeException {

    private final VisitorErrorCode errorCode;
    private final transient Map<String, Object> details;

    public VisitorException(VisitorErrorCode errorCode, Map<String, Object> details) {
        super(errorCode.message());
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public VisitorException(VisitorErrorCode errorCode) {
        this(errorCode, Map.of());
    }

    public VisitorErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> details() {
        return details;
    }

    public static VisitorException of(VisitorErrorCode code) {
        return new VisitorException(code);
    }

    public static VisitorException notFound(String type, UUID id) {
        return new VisitorException(VisitorErrorCode.VISITOR_RECORD_NOT_FOUND,
                Map.of("resourceType", type, "resourceId", id == null ? "" : id.toString()));
    }

    public static VisitorException unauthorizedScope(String site, String resource, String id) {
        return new VisitorException(VisitorErrorCode.VISITOR_UNAUTHORIZED_SCOPE,
                Map.of("siteCode", site == null ? "" : site, "resourceType", resource,
                        "resourceId", id == null ? "" : id));
    }
}
