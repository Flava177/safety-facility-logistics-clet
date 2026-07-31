package gh.edu.clet.sfl.facilities.booking.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaNoShowJpaRepository extends JpaRepository<NoShowRecordEntity, UUID> {

    /**
     * No-show history.
     *
     * <p>{@code from} and {@code to} are non-nullable for the reason set out on
     * {@code JpaBookingJpaRepository.search}: PostgreSQL cannot type a {@code :p is null} test on a
     * null {@code Instant}. The adapter substitutes wide sentinels for an unbounded search.
     */
    @Query("""
            select n from NoShowRecordEntity n
            where (:siteCode is null or n.siteCode = :siteCode)
              and (:requestedBy is null or n.requestedBy = :requestedBy)
              and n.recordedAt >= :from
              and n.recordedAt < :to
            order by n.recordedAt desc
            """)
    List<NoShowRecordEntity> search(@Param("siteCode") String siteCode,
            @Param("requestedBy") String requestedBy,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    @Query("""
            select count(n) from NoShowRecordEntity n
            where n.siteCode = :siteCode and n.recordedAt >= :from
            """)
    long countSince(@Param("siteCode") String siteCode, @Param("from") Instant from);
}
