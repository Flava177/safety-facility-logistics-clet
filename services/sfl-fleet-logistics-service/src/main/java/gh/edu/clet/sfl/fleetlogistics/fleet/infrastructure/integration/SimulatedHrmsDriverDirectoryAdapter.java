package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.integration;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.HrmsDriverDirectoryPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.IntegrationConfigurationNotFoundException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Simulator for the HRMS directory, used until a real HRMS contract is approved (workplan §5: build
 * the adapter with a simulator first, wire the vendor only after the contract tests pass).
 *
 * <p>Two behaviours matter for correctness. First, when {@code sfl.fleet.integration.hrms.provider}
 * names a provider that has no adapter, resolution fails loudly rather than waving the request
 * through. Second, in simulator mode the directory accepts any well-formed staff reference and says
 * so in the log, so nobody can mistake a simulated pass for a verified one.
 */
@Component
public class SimulatedHrmsDriverDirectoryAdapter implements HrmsDriverDirectoryPort {

    private static final Logger log = LoggerFactory.getLogger(SimulatedHrmsDriverDirectoryAdapter.class);
    private static final String SIMULATOR = "simulator";
    /** CLET staff references look like CLET/HR/00123; anything shorter is a typo, not a person. */
    private static final int MIN_REFERENCE_LENGTH = 4;

    private final String configuredProvider;

    SimulatedHrmsDriverDirectoryAdapter(
            @Value("${sfl.fleet.integration.hrms.provider:simulator}") String configuredProvider) {
        this.configuredProvider = configuredProvider;
    }

    @Override
    public void requireEmployedStaff(String staffReference, String siteCode) {
        requireSimulatorMode();
        if (staffReference == null || staffReference.strip().length() < MIN_REFERENCE_LENGTH) {
            throw new IllegalArgumentException("staffReference is not a valid HRMS staff reference");
        }
        log.info("HRMS simulator accepted staff reference {} for site {}. This is a simulated check, not a "
                + "verified HRMS lookup.", staffReference, siteCode);
    }

    @Override
    public Optional<StaffDirectoryEntry> findStaff(String staffReference) {
        requireSimulatorMode();
        if (staffReference == null || staffReference.strip().length() < MIN_REFERENCE_LENGTH) {
            return Optional.empty();
        }
        return Optional.of(new StaffDirectoryEntry(staffReference.strip().toUpperCase(Locale.ROOT),
                "Simulated staff " + staffReference.strip(), null, null, true, null));
    }

    private void requireSimulatorMode() {
        String provider = configuredProvider == null
                ? SIMULATOR
                : configuredProvider.strip().toLowerCase(Locale.ROOT);
        if (!SIMULATOR.equals(provider)) {
            throw new IntegrationConfigurationNotFoundException(Map.of(
                    "capability", "hrms-driver-directory",
                    "configuredProvider", String.valueOf(configuredProvider),
                    "reason", "No adapter is registered for this HRMS provider"));
        }
    }
}
