package gh.edu.clet.sfl.fleetlogistics.fleet.application.port;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.SiteScopeFilter;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowComment;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowPriority;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowTransition;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port for the fleet workflow queue and its append-only history (SRS-SFL-S166-02). */
public interface FleetWorkflowRepository {

    FleetWorkflowItem save(FleetWorkflowItem item);

    Optional<FleetWorkflowItem> findById(UUID id);

    /** An open item already raised for this record, so a repeated observation does not duplicate it. */
    Optional<FleetWorkflowItem> findOpenByRelatedRecord(String relatedRecordType, String relatedRecordId);

    WorkflowPage search(WorkflowSearchCriteria criteria, SiteScopeFilter scope);

    List<FleetWorkflowItem> findAllInScope(SiteScopeFilter scope);

    /** Live items whose resolution target has passed — the input to the escalation sweep. */
    List<FleetWorkflowItem> findLiveBreachedAt(Instant now);

    // --- append-only history -------------------------------------------------------------

    /** Appends a transition. There is no update or delete: the history is immutable. */
    WorkflowTransition appendTransition(WorkflowTransition transition);

    /** Appends a comment. */
    WorkflowComment appendComment(WorkflowComment comment);

    List<WorkflowTransition> findTransitions(UUID workflowItemId);

    List<WorkflowComment> findComments(UUID workflowItemId);

    /** The next sequence number for this item's history. */
    long nextTransitionSequence(UUID workflowItemId);

    record WorkflowSearchCriteria(
            String siteCode,
            FleetWorkflowStatus status,
            FleetWorkflowType workflowType,
            WorkflowPriority priority,
            OperatingMode operatingMode,
            String assignee,
            boolean overdueOnly,
            boolean escalatedOnly,
            Instant from,
            Instant to,
            int page,
            int size,
            String sort) {
    }

    record WorkflowPage(List<FleetWorkflowItem> content, int page, int size, long totalElements, int totalPages,
            String sort) {
    }
}
