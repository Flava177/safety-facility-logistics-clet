package gh.edu.clet.sfl.safetysecurity.lifesafety.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionScheduleJpaRepository extends JpaRepository<InspectionScheduleJpaEntity, UUID> {

    List<InspectionScheduleJpaEntity> findBySiteCode(String siteCode);

    List<InspectionScheduleJpaEntity> findBySiteCodeAndNextDueAtBefore(String siteCode, Instant asOf);

    @org.springframework.data.jpa.repository.Query("select distinct e.siteCode from InspectionScheduleJpaEntity e")
    List<String> findDistinctSiteCodes();
}
