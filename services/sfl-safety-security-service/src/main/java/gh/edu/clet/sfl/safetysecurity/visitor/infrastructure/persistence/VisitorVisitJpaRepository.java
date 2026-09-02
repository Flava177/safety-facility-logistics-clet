package gh.edu.clet.sfl.safetysecurity.visitor.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface VisitorVisitJpaRepository extends JpaRepository<VisitorVisitJpaEntity, UUID> {

    /**
     * {@code from}/{@code to} are never null on the way in - see {@code VisitorRepositoryAdapter}'s
     * unbounded sentinels, the same idiom {@code JpaBookingRepositoryAdapter} uses, because a bare
     * null {@code Instant} parameter leaves PostgreSQL unable to infer the comparison's data type.
     */
    @Query("""
            SELECT v FROM VisitorVisitJpaEntity v
            WHERE (:siteCode IS NULL OR v.siteCode = :siteCode)
              AND (:status IS NULL OR v.status = :status)
              AND (:hostId IS NULL OR v.hostId = :hostId)
              AND v.expectedArrival BETWEEN :from AND :to
            ORDER BY v.expectedArrival DESC
            """)
    List<VisitorVisitJpaEntity> search(@Param("siteCode") String siteCode, @Param("status") VisitStatus status,
            @Param("hostId") String hostId, @Param("from") Instant from, @Param("to") Instant to,
            Pageable pageable);

    List<VisitorVisitJpaEntity> findBySiteCodeAndStatus(String siteCode, VisitStatus status);
}
