package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/** SRS-SFL-S166-01 Unauthorized Scope: the actor may not access this site or record. */
public class FleetAuthorizationException extends FleetDomainException {

    public FleetAuthorizationException() {
        super(FleetErrorCode.FLEET_UNAUTHORIZED_SCOPE);
    }

    public FleetAuthorizationException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_UNAUTHORIZED_SCOPE, details);
    }
}
