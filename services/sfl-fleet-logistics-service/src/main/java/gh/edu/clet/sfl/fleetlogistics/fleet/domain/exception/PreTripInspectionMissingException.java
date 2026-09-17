package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/** No valid pre-trip inspection is recorded, and the trip cannot start without one. */
public class PreTripInspectionMissingException extends FleetDomainException {

    public PreTripInspectionMissingException() {
        super(FleetErrorCode.FLEET_PRETRIP_INSPECTION_MISSING);
    }

    public PreTripInspectionMissingException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_PRETRIP_INSPECTION_MISSING, details);
    }
}
