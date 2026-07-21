package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/** SRS-SFL-S166-05 No Scope: the actor has no site scope assigned. */
public class DashboardScopeMissingException extends FleetDomainException {

    public DashboardScopeMissingException() {
        super(FleetErrorCode.FLEET_DASHBOARD_NO_SCOPE);
    }

    public DashboardScopeMissingException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_DASHBOARD_NO_SCOPE, details);
    }
}
