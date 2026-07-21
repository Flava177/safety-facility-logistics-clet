package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/** SRS-SFL-S166-02 SLA Breach: the item breached its configured SLA. */
public class SlaBreachException extends FleetDomainException {

    public SlaBreachException() {
        super(FleetErrorCode.FLEET_SLA_BREACH);
    }

    public SlaBreachException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_SLA_BREACH, details);
    }
}
