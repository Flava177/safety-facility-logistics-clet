package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowPriority;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Queries over the fleet workflow queue.
 *
 * <p>Top-level by necessity: Spring Data only registers repository interfaces that are top-level
 * types, so a nested interface is silently never created as a bean.
 */
interface FleetWorkflowItemJpaRepository extends JpaRepository<FleetWorkflowItemEntity, UUID> {

    @Query("""
            select i from FleetWorkflowItemEntity i
            where i.relatedRecordType = :relatedRecordType
              and i.relatedRecordId = :relatedRecordId
              and i.status not in (gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowStatus.CLOSED,
                                   gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowStatus.CANCELLED)
            order by i.createdAt desc
            limit 1
            """)
    Optional<FleetWorkflowItemEntity> findOpenByRelatedRecord(
            @Param("relatedRecordType") String relatedRecordType,
            @Param("relatedRecordId") String relatedRecordId);

    /**
     * Paged search.
     *
     * <p>Optional filters use {@code coalesce} rather than {@code :x is null or ...}: PostgreSQL
     * cannot infer the type of a bind parameter that never appears in a typed position — an
     * {@code IS NULL} test, or an argument to {@code upper()} or {@code concat()} — and rejects the
     * prepare with SQLSTATE 42P18. Inside {@code coalesce} the parameter takes its type from the
     * column beside it. Every column used this way is {@code NOT NULL}, so an absent filter still
     * matches every row, exactly as the previous form did.
     */
    @Query("""
            select i from FleetWorkflowItemEntity i
            where (:allSites = true or i.siteCode in :siteScopes)
              and (:siteCode is null or i.siteCode = :siteCode)
              and (:status is null or i.status = :status)
              and (:workflowType is null or i.workflowType = :workflowType)
              and (:priority is null or i.priority = :priority)
              and (:operatingMode is null or i.operatingMode = :operatingMode)
              and (:assignee is null or i.assignee = :assignee)
              and (:overdueOnly = false or (i.slaDueAt < :now
                   and i.status not in (
                       gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowStatus.CLOSED,
                       gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowStatus.CANCELLED)))
              and (:escalatedOnly = false or i.escalationLevel > 0)
              and i.createdAt >= coalesce(:from, i.createdAt)
              and i.createdAt <= coalesce(:to, i.createdAt)
            """)
    Page<FleetWorkflowItemEntity> search(
            @Param("allSites") boolean allSites,
            @Param("siteScopes") List<String> siteScopes,
            @Param("siteCode") String siteCode,
            @Param("status") FleetWorkflowStatus status,
            @Param("workflowType") FleetWorkflowType workflowType,
            @Param("priority") WorkflowPriority priority,
            @Param("operatingMode") OperatingMode operatingMode,
            @Param("assignee") String assignee,
            @Param("overdueOnly") boolean overdueOnly,
            @Param("escalatedOnly") boolean escalatedOnly,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("now") Instant now,
            Pageable pageable);

    @Query("""
            select i from FleetWorkflowItemEntity i
            where (:allSites = true or i.siteCode in :siteScopes)
            """)
    List<FleetWorkflowItemEntity> findAllInScope(@Param("allSites") boolean allSites,
            @Param("siteScopes") List<String> siteScopes);

    @Query("""
            select i from FleetWorkflowItemEntity i
            where i.slaDueAt < :now
              and i.status not in (gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowStatus.CLOSED,
                                   gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowStatus.CANCELLED)
            order by i.slaDueAt asc
            """)
    List<FleetWorkflowItemEntity> findLiveBreachedAt(@Param("now") Instant now);
}
