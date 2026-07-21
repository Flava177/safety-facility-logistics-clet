package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/** A concurrent update changed the record between read and write. */
public class OptimisticLockConflictException extends FleetDomainException {

    public OptimisticLockConflictException() {
        super(FleetErrorCode.FLEET_RECORD_VERSION_CONFLICT);
    }

    public OptimisticLockConflictException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_RECORD_VERSION_CONFLICT, details);
    }
}
