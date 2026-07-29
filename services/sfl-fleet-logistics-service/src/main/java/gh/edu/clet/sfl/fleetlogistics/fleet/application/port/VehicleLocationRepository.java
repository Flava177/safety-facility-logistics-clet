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
    /**
     * One vehicle's movement history, newest first.
     *
     * <p>Closes gap 3. The snapshots have been written on every telematics callback since the
     * service was built and only the latest one was ever readable, so a vehicle detail screen
     * could show where a vehicle is and never where it had been.
     */
    List<VehicleLocationSnapshot> findByVehicle(UUID vehicleId, int limit);

    List<VehicleLocationSnapshot> findRecentInScope(SiteScopeFilter scope, int limit);
}
