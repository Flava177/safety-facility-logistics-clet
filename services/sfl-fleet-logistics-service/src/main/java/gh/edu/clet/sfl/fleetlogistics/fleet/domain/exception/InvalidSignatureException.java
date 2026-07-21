package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/** SRS-SFL-S166-04 Invalid Signature: inbound message failed HMAC or mTLS verification. */
public class InvalidSignatureException extends FleetDomainException {

    public InvalidSignatureException() {
        super(FleetErrorCode.FLEET_INTEGRATION_INVALID_SIGNATURE);
    }

    public InvalidSignatureException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_INTEGRATION_INVALID_SIGNATURE, details);
    }
}
