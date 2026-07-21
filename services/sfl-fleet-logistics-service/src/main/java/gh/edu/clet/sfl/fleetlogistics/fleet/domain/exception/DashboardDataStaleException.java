package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/** SRS-SFL-S166-05 Data Stale: dashboard data exceeds the configured freshness threshold. */
public class DashboardDataStaleException extends FleetDomainException {

    public DashboardDataStaleException() {
        super(FleetErrorCode.FLEET_DASHBOARD_DATA_STALE);
    }

    public DashboardDataStaleException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_DASHBOARD_DATA_STALE, details);
    }
}
