package gh.edu.clet.sfl.fleetlogistics.fleet.application.port;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.SiteScopeFilter;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RegistrationNumber;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Vehicle;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleAvailabilityStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleCategory;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleLifecycleStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleServiceStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for the vehicle register. Owned by the application layer; implemented by the JPA
 * adapter. There is no delete operation — the SRS forbids hard deletion of operational history.
 */
public interface VehicleRepository {

    Vehicle save(Vehicle vehicle);

    Optional<Vehicle> findById(UUID id);

    /**
     * Loads a vehicle and holds a row lock until the transaction ends.
     *
     * <p>Used by assignment so two concurrent bookings of the same vehicle serialise rather than both
     * passing the overlap check.
     */
    Optional<Vehicle> findByIdForUpdate(UUID id);

    /** An active (non-archived) vehicle with this registration in this site, if one exists. */
    Optional<Vehicle> findActiveByRegistration(SiteCode siteCode, RegistrationNumber registrationNumber);

    /** An active (non-archived) vehicle with this VIN in this site, if one exists. */
    Optional<Vehicle> findActiveByVin(SiteCode siteCode, String vin);

    /** Site-filtered, paged search. The filter is applied in SQL, never in memory. */
    VehiclePage search(VehicleSearchCriteria criteria, SiteScopeFilter scope);

    /** Every vehicle in scope, for projections and sweeps. */
    List<Vehicle> findAllInScope(SiteScopeFilter scope);

    /** Search criteria; every field is an optional filter. */
    record VehicleSearchCriteria(
            String siteCode,
            VehicleLifecycleStatus lifecycleStatus,
            VehicleServiceStatus serviceStatus,
            VehicleAvailabilityStatus availabilityStatus,
            VehicleCategory category,
            String responsibleUnit,
            String registrationNumberContains,
            int page,
            int size,
            String sort) {
    }

    /** A page of vehicles plus the totals a client needs to paginate. */
    record VehiclePage(List<Vehicle> content, int page, int size, long totalElements, int totalPages, String sort) {
    }
}
