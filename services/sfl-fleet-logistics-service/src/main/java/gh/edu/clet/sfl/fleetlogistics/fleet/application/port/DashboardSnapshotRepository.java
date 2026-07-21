package gh.edu.clet.sfl.fleetlogistics.fleet.application.port;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetDashboardApplicationService.OperationsDashboardSnapshot;
import java.util.Optional;

/** Persistence port for generated operations dashboard snapshots (SRS-SFL-S166-05). */
public interface DashboardSnapshotRepository {

    OperationsDashboardSnapshot save(OperationsDashboardSnapshot snapshot);

    Optional<OperationsDashboardSnapshot> latestForScope(String scopeKey);
}
