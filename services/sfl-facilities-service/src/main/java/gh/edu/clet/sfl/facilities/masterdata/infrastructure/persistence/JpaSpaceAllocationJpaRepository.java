package gh.edu.clet.sfl.facilities.masterdata.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code facilities.space_allocations}. Every list is filtered in SQL. */
public interface JpaSpaceAllocationJpaRepository extends JpaRepository<SpaceAllocationRecord, UUID> {

    List<SpaceAllocationRecord> findBySiteCodeAndEndedAtIsNullOrderByRoomCodeAscAllocatedUnitAsc(String siteCode);

    List<SpaceAllocationRecord> findByRoomIdAndEndedAtIsNullOrderByAllocatedUnitAsc(UUID roomId);

    List<SpaceAllocationRecord> findBySourceScenarioIdAndEndedAtIsNullOrderByRoomCodeAsc(UUID scenarioId);

    boolean existsBySourceScenarioIdOrEndedByScenarioId(UUID sourceScenarioId, UUID endedByScenarioId);
}
