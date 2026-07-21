package gh.edu.clet.sfl.fleetlogistics.fleet.application.query;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.FleetWorkflowRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetAccessPolicy;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.SiteScopeFilter;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RecordNotFoundException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowComment;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowTransition;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read use cases for the fleet workflow queue and its immutable history. */
@Service
public class FleetWorkflowQueryService {

    private static final String RESOURCE_TYPE = "FleetWorkflowItem";

    private final FleetWorkflowRepository workflowItems;
    private final FleetAccessPolicy accessPolicy;

    public FleetWorkflowQueryService(FleetWorkflowRepository workflowItems, FleetAccessPolicy accessPolicy) {
        this.workflowItems = workflowItems;
        this.accessPolicy = accessPolicy;
    }

    @Transactional(readOnly = true)
    public FleetWorkflowItem findById(UUID itemId, ActorContext actor) {
        accessPolicy.requirePermission(actor, SflPermission.FLEET_WORKFLOW_READ, RESOURCE_TYPE);
        FleetWorkflowItem item = workflowItems.findById(itemId)
                .orElseThrow(() -> RecordNotFoundException.of(RESOURCE_TYPE, itemId));
        accessPolicy.requireSiteAccess(actor, item.siteCode(), RESOURCE_TYPE, itemId.toString());
        return item;
    }

    @Transactional(readOnly = true)
    public FleetWorkflowRepository.WorkflowPage search(
            FleetWorkflowRepository.WorkflowSearchCriteria criteria, ActorContext actor) {
        accessPolicy.requirePermission(actor, SflPermission.FLEET_WORKFLOW_READ, RESOURCE_TYPE);
        SiteScopeFilter scope = accessPolicy.requireSiteScopeFilter(actor);
        return workflowItems.search(criteria, scope);
    }

    @Transactional(readOnly = true)
    public List<WorkflowTransition> findTransitions(UUID itemId, ActorContext actor) {
        FleetWorkflowItem item = findById(itemId, actor);
        return workflowItems.findTransitions(item.id());
    }

    @Transactional(readOnly = true)
    public List<WorkflowComment> findComments(UUID itemId, ActorContext actor) {
        FleetWorkflowItem item = findById(itemId, actor);
        return workflowItems.findComments(item.id());
    }
}
