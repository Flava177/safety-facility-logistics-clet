package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface DashboardSnapshotJpaRepository extends JpaRepository<DashboardSnapshotEntity, UUID> {

    Optional<DashboardSnapshotEntity> findFirstByScopeKeyOrderByGeneratedAtDesc(String scopeKey);
}
