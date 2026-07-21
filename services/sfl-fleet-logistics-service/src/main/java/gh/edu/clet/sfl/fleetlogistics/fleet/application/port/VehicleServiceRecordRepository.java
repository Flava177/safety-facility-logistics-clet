package gh.edu.clet.sfl.fleetlogistics.fleet.application.port;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.SiteScopeFilter;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleServiceRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port for vehicle service history (SRS-SFL-S166-01). */
public interface VehicleServiceRecordRepository {

    VehicleServiceRecord save(VehicleServiceRecord record);

    Optional<VehicleServiceRecord> findById(UUID id);

    /** Full service history for a vehicle, most recent first. */
    List<VehicleServiceRecord> findByVehicle(UUID vehicleId);

    /** The most recent service record, which is the one that sets the current service status. */
    Optional<VehicleServiceRecord> findLatestByVehicle(UUID vehicleId);

    /** Latest record per vehicle in scope, for the service-due sweep and dashboard indicators. */
    List<VehicleServiceRecord> findLatestInScope(SiteScopeFilter scope);
}
