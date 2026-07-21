package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/** SRS-SFL-S166-04 Schema Validation Failed: inbound payload does not match the registered schema. */
public class SchemaValidationFailedException extends FleetDomainException {

    public SchemaValidationFailedException() {
        super(FleetErrorCode.FLEET_INTEGRATION_SCHEMA_INVALID);
    }

    public SchemaValidationFailedException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_INTEGRATION_SCHEMA_INVALID, details);
    }
}
