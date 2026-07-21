package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/** SRS-SFL-S166-05 Restricted Drilldown: the actor may not view the underlying record. */
public class RestrictedDrilldownException extends FleetDomainException {

    public RestrictedDrilldownException() {
        super(FleetErrorCode.FLEET_DASHBOARD_RESTRICTED_DRILLDOWN);
    }

    public RestrictedDrilldownException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_DASHBOARD_RESTRICTED_DRILLDOWN, details);
    }
}
