package gh.edu.clet.sfl.facilities.spaceplanning.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.spaceplanning.domain.OccupancyOverride;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaOccupancyOverrideJpaRepository extends JpaRepository<OccupancyOverrideRecord, UUID> {

    List<OccupancyOverrideRecord> findByScenarioIdOrderByRequestedAtAsc(UUID scenarioId);

    List<OccupancyOverrideRecord> findByScenarioIdInAndStatusNot(Collection<UUID> scenarioIds,
            OccupancyOverride.OverrideStatus status);
}
