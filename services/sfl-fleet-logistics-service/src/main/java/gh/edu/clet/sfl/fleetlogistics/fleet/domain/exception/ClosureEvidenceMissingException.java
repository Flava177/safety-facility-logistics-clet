package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/** SRS-SFL-S166-02 Closure Evidence Missing: closure attempted without the required evidence or reason. */
public class ClosureEvidenceMissingException extends FleetDomainException {

    public ClosureEvidenceMissingException() {
        super(FleetErrorCode.FLEET_CLOSURE_EVIDENCE_MISSING);
    }

    public ClosureEvidenceMissingException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_CLOSURE_EVIDENCE_MISSING, details);
    }
}
