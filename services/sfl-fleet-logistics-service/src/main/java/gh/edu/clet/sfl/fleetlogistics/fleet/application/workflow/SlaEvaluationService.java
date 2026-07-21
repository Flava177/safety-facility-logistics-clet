package gh.edu.clet.sfl.fleetlogistics.fleet.application.workflow;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.FleetWorkflowRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetWorkflowApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowItem;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The scheduled SLA evaluation (SRS-SFL-S166-02 acceptance criterion 3: "Given an SLA threshold is
 * breached, when the scheduled evaluation runs, then the system escalates the item and notifies the
 * configured role").
 *
 * <p>Each breached item is escalated in its own transaction, so one failure does not abandon the rest
 * of the sweep. The escalation itself resolves the SLA from the rules effective at this run, which is
 * what the SRS means by "the runtime configuration active at the time of evaluation".
 */
@Service
public class SlaEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(SlaEvaluationService.class);

    /**
     * The service principal the sweep acts as.
     *
     * <p>It holds the wildcard site scope because SLA breaches must be found across every site, and it
     * is a service account so the audit trail shows the escalation came from the scheduler rather than
     * from a person.
     */
    private static final ActorContext SCHEDULER_ACTOR = new ActorContext(
            new SiteScopedPrincipal("sfl-fleet-sla-scheduler", "Fleet SLA scheduler",
                    Set.of(SflRole.SERVICE_INTEGRATION, SflRole.SFL_ADMIN), Set.of("*"), true),
            null);

    private final FleetWorkflowRepository workflowItems;
    private final FleetWorkflowApplicationService workflowService;
    private final Clock clock;

    public SlaEvaluationService(FleetWorkflowRepository workflowItems,
            FleetWorkflowApplicationService workflowService, Clock clock) {
        this.workflowItems = workflowItems;
        this.workflowService = workflowService;
        this.clock = clock;
    }

    /**
     * Evaluates every live item and escalates those that have breached.
     *
     * @return the items escalated by this pass
     */
    public List<FleetWorkflowItem> evaluateOnce() {
        Instant now = clock.instant();
        List<FleetWorkflowItem> breached = workflowItems.findLiveBreachedAt(now);
        if (breached.isEmpty()) {
            return List.of();
        }

        return breached.stream()
                .filter(item -> item.hasBreachedSlaAt(now))
                .map(item -> escalateSafely(item, correlatedActor(item.id())))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * Escalates one item, logging and continuing if it fails.
     *
     * <p>A single item that cannot be escalated — because somebody closed it in the same instant, for
     * example — must not stop the sweep from handling the others.
     */
    private FleetWorkflowItem escalateSafely(FleetWorkflowItem item, ActorContext actor) {
        try {
            return workflowService.escalateOnSlaBreach(item, actor);
        } catch (RuntimeException exception) {
            log.error("Could not escalate fleet workflow item {} ({}) on SLA breach", item.id(),
                    item.workflowNumber(), exception);
            return null;
        }
    }

    /** Gives each escalation its own correlation id so the audit trail can be followed per item. */
    private static ActorContext correlatedActor(UUID workflowItemId) {
        return new ActorContext(SCHEDULER_ACTOR.principal(), "sla-sweep-" + workflowItemId);
    }
}
