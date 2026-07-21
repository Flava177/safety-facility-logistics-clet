package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/** SRS-SFL-S166-01 Missing Site Scope: a record was saved without a valid site scope or operational owner. */
public class MissingSiteScopeException extends FleetDomainException {

    public MissingSiteScopeException() {
        super(FleetErrorCode.FLEET_MISSING_SITE_SCOPE);
    }

    public MissingSiteScopeException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_MISSING_SITE_SCOPE, details);
    }
}
