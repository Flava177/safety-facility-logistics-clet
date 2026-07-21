package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/** A state-creating command was submitted without an Idempotency-Key header. */
public class IdempotencyKeyRequiredException extends FleetDomainException {

    public IdempotencyKeyRequiredException() {
        super(FleetErrorCode.FLEET_IDEMPOTENCY_KEY_REQUIRED);
    }

    public IdempotencyKeyRequiredException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_IDEMPOTENCY_KEY_REQUIRED, details);
    }
}
