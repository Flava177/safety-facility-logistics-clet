package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/** An odometer reading regressed without an authorised correction workflow. */
public class OdometerRegressionException extends FleetDomainException {

    public OdometerRegressionException() {
        super(FleetErrorCode.FLEET_ODOMETER_REGRESSION);
    }

    public OdometerRegressionException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_ODOMETER_REGRESSION, details);
    }

    public static OdometerRegressionException of(long currentReading, long submittedReading) {
        return new OdometerRegressionException(Map.of(
                "currentReading", currentReading,
                "submittedReading", submittedReading));
    }
}
