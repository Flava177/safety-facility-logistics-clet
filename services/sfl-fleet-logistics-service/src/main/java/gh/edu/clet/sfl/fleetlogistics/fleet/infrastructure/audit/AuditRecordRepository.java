package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Read/insert access to the audit log.
 *
 * <p>There are deliberately no update or delete query methods; the SRS forbids modification by normal
 * application roles and a database trigger enforces the same rule.
 */
public interface AuditRecordRepository extends JpaRepository<AuditRecordEntity, UUID> {

    List<AuditRecordEntity> findAllByOrderBySequenceNoAsc();

    @Query("""
            select a from AuditRecordEntity a
            where (:allSites = true or a.siteScope in :siteScopes)
              and (:resourceType is null or a.resourceType = :resourceType)
              and (:resourceId is null or a.resourceId = :resourceId)
              and (:actorId is null or a.actorId = :actorId)
              and (:action is null or a.action = :action)
              and (:from is null or a.occurredAt >= :from)
              and (:to is null or a.occurredAt <= :to)
            order by a.sequenceNo desc
            """)
    List<AuditRecordEntity> search(
            @Param("allSites") boolean allSites,
            @Param("siteScopes") List<String> siteScopes,
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId,
            @Param("actorId") String actorId,
            @Param("action") gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction action,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}
