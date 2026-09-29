package gh.edu.clet.sfl.facilities.cleaning.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access for one S169 table - see {@code JpaCleaningRepositoryAdapter}. */
public interface JpaCleaningScheduleRepository extends JpaRepository<CleaningScheduleRecord, UUID> {

    @Query("select s from CleaningScheduleRecord s where s.siteCode = :siteCode order by s.name asc")
    List<CleaningScheduleRecord> findForSite(@Param("siteCode") String siteCode);

    @Query("select s from CleaningScheduleRecord s where s.active = true order by s.siteCode asc, s.name asc")
    List<CleaningScheduleRecord> findActive();
}
