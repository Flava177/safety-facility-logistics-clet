package gh.edu.clet.sfl.safetysecurity.cctv.domain.exception;

import java.util.Map;
import java.util.UUID;

/** The single domain/application exception for S161. Framework-free, mirrors {@code AccessControlException}. */
public class CctvException extends RuntimeException {

    private final CctvErrorCode errorCode;
    private final transient Map<String, Object> details;

    public CctvException(CctvErrorCode errorCode, Map<String, Object> details) {
        super(errorCode.message());
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public CctvException(CctvErrorCode errorCode) {
        this(errorCode, Map.of());
    }

    public CctvErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> details() {
        return details;
    }

    public static CctvException of(CctvErrorCode code) {
        return new CctvException(code);
    }

    public static CctvException notFound(String type, UUID id) {
        return new CctvException(CctvErrorCode.CCTV_RECORD_NOT_FOUND,
                Map.of("resourceType", type, "resourceId", id == null ? "" : id.toString()));
    }

    public static CctvException unauthorizedScope(String site, String resource, String id) {
        return new CctvException(CctvErrorCode.CCTV_UNAUTHORIZED_SCOPE,
                Map.of("siteCode", site == null ? "" : site, "resourceType", resource,
                        "resourceId", id == null ? "" : id));
    }
}
