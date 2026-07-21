package gh.edu.clet.sfl.fleetlogistics.fleet.application.port;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.SiteScopeFilter;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleLocationSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Vehicle movement projection persistence port (SRS-SFL-S166-04/05). */
public interface VehicleLocationRepository {

    VehicleLocationSnapshot save(VehicleLocationSnapshot snapshot);

    Optional<VehicleLocationSnapshot> findLatestByVehicle(UUID vehicleId);

    List<VehicleLocationSnapshot> findRecentInScope(SiteScopeFilter scope, int limit);
}
