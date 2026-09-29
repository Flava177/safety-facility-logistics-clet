package gh.edu.clet.sfl.facilities.spaceplanning.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.spaceplanning.domain.ScenarioStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaSpaceScenarioJpaRepository extends JpaRepository<SpaceScenarioRecord, UUID> {

    @Query("""
            select s from SpaceScenarioRecord s
            where s.siteCode = :siteCode and (:status is null or s.status = :status)
            order by s.planReference asc, s.versionNumber asc
            """)
    List<SpaceScenarioRecord> search(@Param("siteCode") String siteCode, @Param("status") ScenarioStatus status);

    @Query("select coalesce(max(s.versionNumber), 0) from SpaceScenarioRecord s where s.planReference = :plan")
    int maxVersion(@Param("plan") String planReference);

    @Query(value = "select nextval('facilities.space_plan_reference_seq')", nativeQuery = true)
    long nextPlanNumber();

    @Query(value = "select nextval('facilities.space_change_request_reference_seq')", nativeQuery = true)
    long nextRequestNumber();
}
