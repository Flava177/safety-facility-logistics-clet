package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/** A state machine rejected the requested transition. */
public class InvalidStateTransitionException extends FleetDomainException {

    public InvalidStateTransitionException() {
        super(FleetErrorCode.FLEET_INVALID_STATE_TRANSITION);
    }

    public InvalidStateTransitionException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_INVALID_STATE_TRANSITION, details);
    }

    public static InvalidStateTransitionException of(String aggregate, Object from, Object to) {
        return new InvalidStateTransitionException(Map.of(
                "aggregate", aggregate,
                "fromStatus", String.valueOf(from),
                "toStatus", String.valueOf(to)));
    }
}
