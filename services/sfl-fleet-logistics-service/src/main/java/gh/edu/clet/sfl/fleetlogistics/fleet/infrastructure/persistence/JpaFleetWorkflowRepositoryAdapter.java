package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.FleetWorkflowRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.SlaRuleRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.SiteScopeFilter;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowComment;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowTransition;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy.SlaPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for the workflow queue, its append-only history and the SLA rules. */
@Component
class JpaFleetWorkflowRepositoryAdapter implements FleetWorkflowRepository, SlaRuleRepository {

    private final FleetWorkflowItemJpaRepository items;
    private final WorkflowTransitionJpaRepository transitions;
    private final WorkflowCommentJpaRepository comments;
    private final SlaRuleJpaRepository slaRules;
    private final Clock clock;

    JpaFleetWorkflowRepositoryAdapter(FleetWorkflowItemJpaRepository items,
            WorkflowTransitionJpaRepository transitions, WorkflowCommentJpaRepository comments,
            SlaRuleJpaRepository slaRules, Clock clock) {
        this.items = items;
        this.transitions = transitions;
        this.comments = comments;
        this.slaRules = slaRules;
        this.clock = clock;
    }

    @Override
    @Transactional
    public FleetWorkflowItem save(FleetWorkflowItem item) {
        FleetWorkflowItemEntity entity = items.findById(item.id())
                .map(existing -> {
                    existing.applyFrom(item);
                    return existing;
                })
                .orElseGet(() -> FleetWorkflowItemEntity.from(item));
        return items.save(entity).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FleetWorkflowItem> findById(UUID id) {
        return items.findById(id).map(FleetWorkflowItemEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FleetWorkflowItem> findOpenByRelatedRecord(String relatedRecordType, String relatedRecordId) {
        if (relatedRecordType == null || relatedRecordId == null) {
            return Optional.empty();
        }
        return items.findOpenByRelatedRecord(relatedRecordType, relatedRecordId)
                .map(FleetWorkflowItemEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkflowPage search(WorkflowSearchCriteria criteria, SiteScopeFilter scope) {
        Page<FleetWorkflowItemEntity> page = items.search(
                scope.allSites(), scopeList(scope), normalise(criteria.siteCode()), criteria.status(),
                criteria.workflowType(), criteria.priority(), criteria.operatingMode(), criteria.assignee(),
                criteria.overdueOnly(), criteria.escalatedOnly(), criteria.from(), criteria.to(), clock.instant(),
                JpaVehicleRepositoryAdapter.pageRequest(criteria.page(), criteria.size(),
                        criteria.sort() == null ? "slaDueAt,asc" : criteria.sort()));

        return new WorkflowPage(page.getContent().stream().map(FleetWorkflowItemEntity::toDomain).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(),
                page.getSort().toString());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FleetWorkflowItem> findAllInScope(SiteScopeFilter scope) {
        return items.findAllInScope(scope.allSites(), scopeList(scope)).stream()
                .map(FleetWorkflowItemEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FleetWorkflowItem> findLiveBreachedAt(Instant now) {
        return items.findLiveBreachedAt(now).stream().map(FleetWorkflowItemEntity::toDomain).toList();
    }

    @Override
    @Transactional
    public WorkflowTransition appendTransition(WorkflowTransition transition) {
        return transitions.save(WorkflowTransitionEntity.from(transition)).toDomain();
    }

    @Override
    @Transactional
    public WorkflowComment appendComment(WorkflowComment comment) {
        return comments.save(WorkflowCommentEntity.from(comment)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkflowTransition> findTransitions(UUID workflowItemId) {
        return transitions.findByWorkflowItemIdOrderBySequenceAsc(workflowItemId).stream()
                .map(WorkflowTransitionEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkflowComment> findComments(UUID workflowItemId) {
        return comments.findByWorkflowItemIdOrderByOccurredAtAsc(workflowItemId).stream()
                .map(WorkflowCommentEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long nextTransitionSequence(UUID workflowItemId) {
        return transitions.maxSequence(workflowItemId) + 1;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlaPolicy.SlaRule> findEffectiveRules(Instant at) {
        return slaRules.findEffectiveAt(at).stream().map(SlaRuleEntity::toDomain).toList();
    }

    private static List<String> scopeList(SiteScopeFilter scope) {
        return scope.allSites() ? List.of("*") : List.copyOf(scope.sites());
    }

    private static String normalise(String siteCode) {
        return siteCode == null || siteCode.isBlank() ? null : siteCode.strip().toUpperCase(Locale.ROOT);
    }
}
