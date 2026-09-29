package gh.edu.clet.sfl.facilities.cleaning.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.cleaning.domain.ReservationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access for one S169 table - see {@code JpaCleaningRepositoryAdapter}. */
public interface JpaCleaningReservationRepository extends JpaRepository<CleaningCapacityReservationRecord, UUID> {

    @Query("""
            select r from CleaningCapacityReservationRecord r
             where r.siteCode = :siteCode and r.eventReference = :eventReference and r.status = :status
            """)
    Optional<CleaningCapacityReservationRecord> findByEvent(@Param("siteCode") String siteCode,
            @Param("eventReference") String eventReference, @Param("status") ReservationStatus status);

    @Query("""
            select r from CleaningCapacityReservationRecord r
             where r.siteCode = :siteCode and r.windowFrom < :to and r.windowTo > :from
             order by r.windowFrom asc
            """)
    List<CleaningCapacityReservationRecord> findOverlapping(@Param("siteCode") String siteCode,
            @Param("from") Instant from, @Param("to") Instant to);
}
