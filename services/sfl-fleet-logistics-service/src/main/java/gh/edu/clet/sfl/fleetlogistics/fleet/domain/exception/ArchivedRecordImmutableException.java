package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/** An archived record was edited outside an authorised restoration workflow. */
public class ArchivedRecordImmutableException extends FleetDomainException {

    public ArchivedRecordImmutableException() {
        super(FleetErrorCode.FLEET_ARCHIVED_RECORD_IMMUTABLE);
    }

    public ArchivedRecordImmutableException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_ARCHIVED_RECORD_IMMUTABLE, details);
    }
}
