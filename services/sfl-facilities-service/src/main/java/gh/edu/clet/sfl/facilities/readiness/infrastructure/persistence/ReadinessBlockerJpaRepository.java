package gh.edu.clet.sfl.facilities.readiness.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSeverity;
import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSource;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ReadinessBlockerJpaRepository extends JpaRepository<ReadinessBlockerEntity, UUID> {

    /** Open blockers for one space, worst first. The input to the readiness evaluation. */
    List<ReadinessBlockerEntity> findByRoomIdAndResolvedFalseOrderBySeverityAscRaisedAtAsc(UUID roomId);

    List<ReadinessBlockerEntity> findBySiteCodeAndResolvedFalseOrderBySeverityAscRaisedAtAsc(String siteCode);

    List<ReadinessBlockerEntity> findByResolvedFalseOrderBySeverityAscRaisedAtAsc();

    List<ReadinessBlockerEntity> findBySourceAndSourceReferenceAndResolvedFalse(BlockerSource source,
            String sourceReference);

    @Query("""
            select b from ReadinessBlockerEntity b
            where (:siteCode is null or b.siteCode = :siteCode)
              and (:roomId is null or b.roomId = :roomId)
              and (:severity is null or b.severity = :severity)
              and (:resolved is null or b.resolved = :resolved)
            order by b.resolved asc, b.severity asc, b.raisedAt asc
            """)
    List<ReadinessBlockerEntity> search(
            @Param("siteCode") String siteCode,
            @Param("roomId") UUID roomId,
            @Param("severity") BlockerSeverity severity,
            @Param("resolved") Boolean resolved,
            Pageable pageable);
}
