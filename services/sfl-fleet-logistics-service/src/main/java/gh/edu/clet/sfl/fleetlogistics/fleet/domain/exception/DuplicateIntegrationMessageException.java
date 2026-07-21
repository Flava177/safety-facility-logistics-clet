package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/** SRS-SFL-S166-04 Duplicate Message: the message was already accepted and is safely ignored. */
public class DuplicateIntegrationMessageException extends FleetDomainException {

    public DuplicateIntegrationMessageException() {
        super(FleetErrorCode.FLEET_INTEGRATION_DUPLICATE_MESSAGE);
    }

    public DuplicateIntegrationMessageException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_INTEGRATION_DUPLICATE_MESSAGE, details);
    }
}
