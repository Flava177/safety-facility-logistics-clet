package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Base type for every fleet business failure. Carries a {@link FleetErrorCode} so the API layer can
 * map to a status code and error envelope without the domain knowing anything about HTTP.
 *
 * <p>{@link #details()} carries machine-readable context (blocker codes, conflicting identifiers,
 * expected versions) that the API layer surfaces alongside the message.
 */
public abstract class FleetDomainException extends RuntimeException {

    private final FleetErrorCode errorCode;
    private final transient Map<String, Object> details;

    protected FleetDomainException(FleetErrorCode errorCode) {
        this(errorCode, errorCode.message(), Map.of());
    }

    protected FleetDomainException(FleetErrorCode errorCode, Map<String, Object> details) {
        this(errorCode, errorCode.message(), details);
    }

    protected FleetDomainException(FleetErrorCode errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode is required");
        this.details = details == null || details.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public FleetErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> details() {
        return details;
    }
}
