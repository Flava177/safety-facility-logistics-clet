package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.DashboardSnapshotRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetDashboardApplicationService.OperationsDashboardSnapshot;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for operations dashboard snapshots. */
@Component
class JpaDashboardSnapshotRepositoryAdapter implements DashboardSnapshotRepository {

    private final DashboardSnapshotJpaRepository snapshots;

    JpaDashboardSnapshotRepositoryAdapter(DashboardSnapshotJpaRepository snapshots) {
        this.snapshots = snapshots;
    }

    @Override
    @Transactional
    public OperationsDashboardSnapshot save(OperationsDashboardSnapshot snapshot) {
        return snapshots.save(DashboardSnapshotEntity.from(snapshot)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OperationsDashboardSnapshot> latestForScope(String scopeKey) {
        return snapshots.findFirstByScopeKeyOrderByGeneratedAtDesc(scopeKey).map(DashboardSnapshotEntity::toDomain);
    }
}
