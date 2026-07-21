package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/** An Idempotency-Key was replayed with a different request payload. */
public class IdempotencyKeyConflictException extends FleetDomainException {

    public IdempotencyKeyConflictException() {
        super(FleetErrorCode.FLEET_IDEMPOTENCY_KEY_CONFLICT);
    }

    public IdempotencyKeyConflictException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_IDEMPOTENCY_KEY_CONFLICT, details);
    }
}
