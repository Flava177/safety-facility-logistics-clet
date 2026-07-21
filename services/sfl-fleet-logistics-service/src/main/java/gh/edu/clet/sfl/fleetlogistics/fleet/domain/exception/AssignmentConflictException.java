package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/** An overlapping active assignment exists for the vehicle or the driver. */
public class AssignmentConflictException extends FleetDomainException {

    public AssignmentConflictException() {
        super(FleetErrorCode.FLEET_ASSIGNMENT_CONFLICT);
    }

    public AssignmentConflictException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_ASSIGNMENT_CONFLICT, details);
    }
}
