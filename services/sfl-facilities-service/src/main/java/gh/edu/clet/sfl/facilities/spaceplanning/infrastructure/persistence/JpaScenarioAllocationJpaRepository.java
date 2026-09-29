package gh.edu.clet.sfl.facilities.spaceplanning.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaScenarioAllocationJpaRepository extends JpaRepository<ScenarioAllocationRecord, UUID> {

    List<ScenarioAllocationRecord> findByScenarioIdOrderByRoomCodeAscAllocatedUnitAsc(UUID scenarioId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ScenarioAllocationRecord a where a.scenarioId = :scenarioId and a.roomId = :roomId")
    int deleteForRoom(@Param("scenarioId") UUID scenarioId, @Param("roomId") UUID roomId);
}
