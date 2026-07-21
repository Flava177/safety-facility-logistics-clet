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

/** Spring Data interfaces backing the fleet workflow adapter, grouped so they stay together. */
final class FleetWorkflowJpaRepositories {

    private FleetWorkflowJpaRepositories() {
    }

    interface Items extends JpaRepository<FleetWorkflowItemEntity, UUID> {

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
                  and (:from is null or i.createdAt >= :from)
                  and (:to is null or i.createdAt <= :to)
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

    interface Transitions extends JpaRepository<WorkflowTransitionEntity, UUID> {

        List<WorkflowTransitionEntity> findByWorkflowItemIdOrderBySequenceAsc(UUID workflowItemId);

        @Query("select coalesce(max(t.sequence), -1) from WorkflowTransitionEntity t "
                + "where t.workflowItemId = :workflowItemId")
        long maxSequence(@Param("workflowItemId") UUID workflowItemId);
    }

    interface Comments extends JpaRepository<WorkflowCommentEntity, UUID> {

        List<WorkflowCommentEntity> findByWorkflowItemIdOrderByOccurredAtAsc(UUID workflowItemId);
    }

    interface SlaRules extends JpaRepository<SlaRuleEntity, UUID> {

        @Query("""
                select r from SlaRuleEntity r
                where r.effectiveFrom <= :at
                  and (r.effectiveTo is null or r.effectiveTo > :at)
                """)
        List<SlaRuleEntity> findEffectiveAt(@Param("at") Instant at);
    }
}
