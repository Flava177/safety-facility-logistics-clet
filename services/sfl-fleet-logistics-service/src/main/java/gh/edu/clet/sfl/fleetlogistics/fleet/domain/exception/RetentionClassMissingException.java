package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/** SRS-SFL-S166-03 Retention Class Missing: evidence saved without a retention class. */
public class RetentionClassMissingException extends FleetDomainException {

    public RetentionClassMissingException() {
        super(FleetErrorCode.FLEET_RETENTION_CLASS_MISSING);
    }

    public RetentionClassMissingException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_RETENTION_CLASS_MISSING, details);
    }
}
