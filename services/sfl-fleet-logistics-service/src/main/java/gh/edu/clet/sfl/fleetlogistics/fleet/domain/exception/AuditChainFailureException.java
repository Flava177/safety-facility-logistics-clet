package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/** SRS-SFL-S166-03 Audit Chain Failure: hash-chain replay detected tampering. */
public class AuditChainFailureException extends FleetDomainException {

    public AuditChainFailureException() {
        super(FleetErrorCode.FLEET_AUDIT_CHAIN_FAILURE);
    }

    public AuditChainFailureException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_AUDIT_CHAIN_FAILURE, details);
    }
}
