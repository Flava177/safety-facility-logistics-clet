package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/** The referenced fleet record does not exist or is outside the actor's scope. */
public class RecordNotFoundException extends FleetDomainException {

    public RecordNotFoundException() {
        super(FleetErrorCode.FLEET_RECORD_NOT_FOUND);
    }

    public RecordNotFoundException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_RECORD_NOT_FOUND, details);
    }

    public static RecordNotFoundException of(String recordType, Object identifier) {
        return new RecordNotFoundException(Map.of("recordType", recordType, "identifier", String.valueOf(identifier)));
    }
}
