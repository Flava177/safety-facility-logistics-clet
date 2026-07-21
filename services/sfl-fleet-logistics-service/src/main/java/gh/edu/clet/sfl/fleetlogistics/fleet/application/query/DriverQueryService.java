package gh.edu.clet.sfl.fleetlogistics.fleet.application.query;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.DriverProfileRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.RuntimeConfigurationPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetAccessPolicy;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.SiteScopeFilter;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RecordNotFoundException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverProfileReference;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EligibilityAssessment;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleCategory;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy.DriverEligibilityPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read use cases for driver profile references, including the on-demand eligibility assessment. */
@Service
public class DriverQueryService {

    private static final String RESOURCE_TYPE = "DriverProfileReference";

    private final DriverProfileRepository drivers;
    private final FleetAccessPolicy accessPolicy;
    private final RuntimeConfigurationPort runtimeConfiguration;
    private final Clock clock;

    public DriverQueryService(DriverProfileRepository drivers, FleetAccessPolicy accessPolicy,
            RuntimeConfigurationPort runtimeConfiguration, Clock clock) {
        this.drivers = drivers;
        this.accessPolicy = accessPolicy;
        this.runtimeConfiguration = runtimeConfiguration;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DriverProfileReference findById(UUID driverId, ActorContext actor) {
        accessPolicy.requirePermission(actor, SflPermission.FLEET_DRIVER_READ, RESOURCE_TYPE);
        DriverProfileReference driver = drivers.findById(driverId)
                .orElseThrow(() -> RecordNotFoundException.of(RESOURCE_TYPE, driverId));
        accessPolicy.requireSiteAccess(actor, driver.siteCode(), RESOURCE_TYPE, driverId.toString());
        return driver;
    }

    @Transactional(readOnly = true)
    public DriverProfileRepository.DriverPage search(DriverProfileRepository.DriverSearchCriteria criteria,
            ActorContext actor) {
        accessPolicy.requirePermission(actor, SflPermission.FLEET_DRIVER_READ, RESOURCE_TYPE);
        SiteScopeFilter scope = accessPolicy.requireSiteScopeFilter(actor);
        return drivers.search(criteria, scope);
    }

    /**
     * Assesses eligibility, optionally for a specific vehicle category and period.
     *
     * <p>Computed on read rather than returned from the stored status, so the answer reflects the
     * licence and clearance dates as at this moment — a stored status can be a day stale.
     */
    @Transactional(readOnly = true)
    public EligibilityAssessment assessEligibility(UUID driverId, VehicleCategory vehicleCategory,
            Instant periodEnd, ActorContext actor) {
        DriverProfileReference driver = findById(driverId, actor);
        return DriverEligibilityPolicy.assess(driver, vehicleCategory, clock.instant(), periodEnd,
                runtimeConfiguration.complianceExpiryWarningWindow(driver.siteCode().value()), null);
    }

    /** Whether the caller may see the unmasked licence number. */
    public boolean canReadSensitive(ActorContext actor) {
        return accessPolicy.canReadSensitive(actor, SflPermission.FLEET_DRIVER_SENSITIVE_READ);
    }
}
