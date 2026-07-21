package gh.edu.clet.sfl.fleetlogistics.fleet.application.port;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.SiteScopeFilter;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleInspection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port for vehicle inspections (traced to SRS-SFL-S166-01 and -02; gap report C-01). */
public interface VehicleInspectionRepository {

    VehicleInspection save(VehicleInspection inspection);

    Optional<VehicleInspection> findById(UUID id);

    /** The most recent inspection for a vehicle — the one readiness judges. */
    Optional<VehicleInspection> findLatestByVehicle(UUID vehicleId);

    List<VehicleInspection> findByVehicle(UUID vehicleId);

    List<VehicleInspection> findByTrip(UUID tripId);

    /** Latest inspection per vehicle in scope, for the dashboard's inspection-failure indicator. */
    List<VehicleInspection> findLatestInScope(SiteScopeFilter scope);

    /** Failed inspections in scope since {@code from}, for the exceptions view. */
    List<VehicleInspection> findFailuresSince(SiteScopeFilter scope, Instant from);
}
