package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/** SRS-SFL-S166-02 Unauthorized Approval: the actor may not approve this workflow transition. */
public class UnauthorizedApprovalException extends FleetDomainException {

    public UnauthorizedApprovalException() {
        super(FleetErrorCode.FLEET_UNAUTHORIZED_APPROVAL);
    }

    public UnauthorizedApprovalException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_UNAUTHORIZED_APPROVAL, details);
    }
}
