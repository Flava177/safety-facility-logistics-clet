package gh.edu.clet.sfl.facilities.readiness.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ReadinessChecklistJpaRepository extends JpaRepository<ReadinessChecklistEntity, UUID> {

    Optional<ReadinessChecklistEntity> findBySiteCodeAndChecklistCode(String siteCode, String checklistCode);

    List<ReadinessChecklistEntity> findBySiteCodeOrderByChecklistCodeAsc(String siteCode);

    List<ReadinessChecklistEntity> findAllByOrderBySiteCodeAscChecklistCodeAsc();

    /**
     * Active checklists that could apply to a space of this type in this mode.
     *
     * <p>Returns every candidate rather than one: "most specific wins" is a domain rule, and encoding
     * it as an ORDER BY here would hide it in a query string. The adapter picks.
     */
    @Query("""
            select c from ReadinessChecklistEntity c
            where c.siteCode = :siteCode
              and c.lifecycleStatus = gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus.ACTIVE
              and (c.spaceType is null or c.spaceType = :spaceType)
              and (c.operatingMode is null or c.operatingMode = :operatingMode)
            """)
    List<ReadinessChecklistEntity> findCandidates(@Param("siteCode") String siteCode,
            @Param("spaceType") SpaceType spaceType, @Param("operatingMode") OperatingMode operatingMode);
}
