package gh.edu.clet.sfl.facilities.eventlogistics.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaEventSetupTaskRepository extends JpaRepository<EventSetupTaskRecord, UUID> {

    @Query("select t from EventSetupTaskRecord t where t.s078EventReference = :reference")
    Optional<EventSetupTaskRecord> findByReference(@Param("reference") String reference);

    /**
     * Site is optional ({@code null} = every site); the instants never are, because a null
     * {@code Instant} parameter cannot be typed by PostgreSQL - see {@code JpaBookingRepositoryAdapter}.
     */
    @Query("""
            select t from EventSetupTaskRecord t
            where (:siteCode is null or t.siteCode = :siteCode)
              and t.startsAt >= :fromInstant and t.startsAt < :toInstant
            order by t.startsAt asc
            """)
    List<EventSetupTaskRecord> findStartingBetween(@Param("siteCode") String siteCode,
            @Param("fromInstant") Instant from, @Param("toInstant") Instant to, Pageable pageable);

    @Query("""
            select t from EventSetupTaskRecord t
            where t.status in (gh.edu.clet.sfl.facilities.eventlogistics.domain.EventSetupTaskStatus.OPEN,
                               gh.edu.clet.sfl.facilities.eventlogistics.domain.EventSetupTaskStatus.CONFIRMED)
              and t.startsAt < :startsBefore and t.endsAt > :endsAfter
            order by t.startsAt asc
            """)
    List<EventSetupTaskRecord> findLiveForEscalation(@Param("startsBefore") Instant startsBefore,
            @Param("endsAfter") Instant endsAfter, Pageable pageable);

    @Query(value = "select nextval('facilities.event_setup_task_reference_seq')", nativeQuery = true)
    long nextTaskSequence();
}
