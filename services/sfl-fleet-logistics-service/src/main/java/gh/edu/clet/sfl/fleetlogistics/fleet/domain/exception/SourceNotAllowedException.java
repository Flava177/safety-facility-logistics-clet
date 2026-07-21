package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/** SRS-SFL-S166-04: the calling source system is not on the allowlist. */
public class SourceNotAllowedException extends FleetDomainException {

    public SourceNotAllowedException() {
        super(FleetErrorCode.FLEET_INTEGRATION_SOURCE_NOT_ALLOWED);
    }

    public SourceNotAllowedException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_INTEGRATION_SOURCE_NOT_ALLOWED, details);
    }
}
