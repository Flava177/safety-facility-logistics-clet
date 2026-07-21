package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/** The driver is not eligible for the requested assignment. */
public class DriverIneligibleException extends FleetDomainException {

    public DriverIneligibleException() {
        super(FleetErrorCode.FLEET_DRIVER_INELIGIBLE);
    }

    public DriverIneligibleException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_DRIVER_INELIGIBLE, details);
    }
}
