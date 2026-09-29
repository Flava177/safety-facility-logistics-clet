package gh.edu.clet.sfl.facilities.energy.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.energy.domain.ReadingStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EnergyReadingJpaRepository extends JpaRepository<EnergyReadingRecord, UUID> {

    Optional<EnergyReadingRecord> findBySourceReference(String sourceReference);

    Optional<EnergyReadingRecord> findFirstByMeterIdAndStatusOrderByObservedAtDesc(UUID meterId, ReadingStatus status);

    List<EnergyReadingRecord> findByMeterIdAndStatusAndObservedAtGreaterThanEqualAndObservedAtLessThanOrderByObservedAtAsc(
            UUID meterId, ReadingStatus status, Instant from, Instant to);

    /** Temporal bounds are never null - see JpaBookingRepositoryAdapter on why pgjdbc cannot type a null Instant. */
    @Query("""
            select r from EnergyReadingRecord r
             where (:site is null or r.siteCode = :site)
               and (:meterId is null or r.meterId = :meterId)
               and (:status is null or r.status = :status)
               and r.observedAt >= :from and r.observedAt < :to
             order by r.observedAt desc
            """)
    List<EnergyReadingRecord> search(@Param("site") String site, @Param("meterId") UUID meterId,
            @Param("status") ReadingStatus status, @Param("from") Instant from, @Param("to") Instant to,
            Pageable page);

    @Modifying
    @Query("delete from EnergyReadingRecord r where r.observedAt < :cutoff and r.status <> :held")
    int purge(@Param("cutoff") Instant cutoff, @Param("held") ReadingStatus held);
}
