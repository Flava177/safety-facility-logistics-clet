package gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaPreventiveScheduleRepository extends JpaRepository<PreventiveScheduleRecord, UUID> {

    Optional<PreventiveScheduleRecord> findBySiteCodeAndScheduleCode(String siteCode, String scheduleCode);

    @Query("""
            select s from PreventiveScheduleRecord s
            where (:siteCode is null or s.siteCode = :siteCode)
              and (:assetId is null or s.assetId = :assetId)
            order by s.nextDueOn asc
            """)
    List<PreventiveScheduleRecord> search(@Param("siteCode") String siteCode,
            @Param("assetId") UUID assetId);

    /**
     * Active schedules whose next service falls inside the lead-time window.
     *
     * <p>Narrowed by date here and re-checked in the aggregate. This query cannot express
     * "not already generated for this cycle" cleanly across null last-generated dates, and the
     * aggregate is where that rule belongs anyway — the query is an index-friendly first cut, not
     * the decision.
     */
    @Query(value = """
            select * from facilities.preventive_schedules s
            where s.lifecycle_status = 'ACTIVE'
              and (s.next_due_on - s.lead_time_days) <= :today
              and (s.last_generated_for is null or s.last_generated_for < s.next_due_on)
            order by s.next_due_on asc
            limit :batchSize
            """, nativeQuery = true)
    List<PreventiveScheduleRecord> findDue(@Param("today") LocalDate today,
            @Param("batchSize") int batchSize);
}
