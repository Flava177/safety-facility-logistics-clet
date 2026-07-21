package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/** Assignment was blocked by one or more readiness blockers. */
public class ReadinessBlockedException extends FleetDomainException {

    public ReadinessBlockedException() {
        super(FleetErrorCode.FLEET_READINESS_BLOCKED);
    }

    public ReadinessBlockedException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_READINESS_BLOCKED, details);
    }
}
