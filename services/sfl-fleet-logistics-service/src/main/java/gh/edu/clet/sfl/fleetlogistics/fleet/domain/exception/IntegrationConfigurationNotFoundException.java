package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/** SRS-SFL-S166-04: a required integration adapter has no active configuration. Fail loud, never fall back silently. */
public class IntegrationConfigurationNotFoundException extends FleetDomainException {

    public IntegrationConfigurationNotFoundException() {
        super(FleetErrorCode.FLEET_INTEGRATION_NOT_CONFIGURED);
    }

    public IntegrationConfigurationNotFoundException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_INTEGRATION_NOT_CONFIGURED, details);
    }

    public static IntegrationConfigurationNotFoundException forCapability(String capability) {
        return new IntegrationConfigurationNotFoundException(Map.of("capability", capability));
    }
}
