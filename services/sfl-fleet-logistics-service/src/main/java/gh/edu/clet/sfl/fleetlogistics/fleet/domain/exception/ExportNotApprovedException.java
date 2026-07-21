package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/** SRS-SFL-S166-03 Export Not Approved: evidence export attempted without an approved reason. */
public class ExportNotApprovedException extends FleetDomainException {

    public ExportNotApprovedException() {
        super(FleetErrorCode.FLEET_EXPORT_NOT_APPROVED);
    }

    public ExportNotApprovedException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_EXPORT_NOT_APPROVED, details);
    }
}
