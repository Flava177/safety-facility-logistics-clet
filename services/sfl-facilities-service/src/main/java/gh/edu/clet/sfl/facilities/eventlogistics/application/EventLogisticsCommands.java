package gh.edu.clet.sfl.facilities.eventlogistics.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.DeliveryOutcome;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventResourceType;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The S173 commands. There is deliberately no "create set-up task" command: a task is created only by an
 * accepted S078 hand-off (SRS-SFL-S173-01), which arrives as a signed vendor message, not a command.
 */
public final class EventLogisticsCommands {

    private EventLogisticsCommands() {
    }

    /**
     * SRS-SFL-S173-01: the coordinator decomposes the hand-off into typed requests.
     *
     * @param applyTemplate also raise the category's persistent template lines (S173-04 feedback) for any
     *        resource type the coordinator did not list
     */
    public record DecomposeSetupTask(UUID setupTaskId, List<NewResourceRequest> requests, boolean applyTemplate,
            ActorContext actor, SourceChannel channel) {

        public DecomposeSetupTask {
            requests = requests == null ? List.of() : List.copyOf(requests);
        }
    }

    /**
     * @param neededFrom when the resource is needed from; the event start when absent
     * @param neededTo when it is needed until; the event end when absent
     */
    public record NewResourceRequest(EventResourceType resourceType, String description, Integer quantity,
            UUID bookableResourceId, Instant neededFrom, Instant neededTo) {
    }

    /** Re-send one request to its owning system - after a conflict, or once the venue is booked. */
    public record RouteResourceRequest(UUID resourceRequestId, ActorContext actor, SourceChannel channel) {
    }

    /** SRS-SFL-S173-04: a named person takes responsibility for a manual coordination item. */
    public record AcceptManualCoordination(UUID resourceRequestId, String arrangedWith, ActorContext actor,
            SourceChannel channel) {
    }

    public record CancelResourceRequest(UUID resourceRequestId, String reason, ActorContext actor,
            SourceChannel channel) {
    }

    /** SRS-SFL-S173-03. {@code version} null links the latest published version S173 knows of. */
    public record LinkRiskAssessment(UUID setupTaskId, String assessmentId, Integer version, ActorContext actor,
            SourceChannel channel) {
    }

    public record ConfirmSetupTask(UUID setupTaskId, ActorContext actor, SourceChannel channel) {
    }

    public record CompleteSetupTask(UUID setupTaskId, String notes, ActorContext actor, SourceChannel channel) {
    }

    /** SRS-SFL-S173-04 post-event reconciliation, one line per resource request. */
    public record RecordReconciliation(UUID setupTaskId, List<ReconciliationEntry> lines, ActorContext actor,
            SourceChannel channel) {

        public RecordReconciliation {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    public record ReconciliationEntry(UUID resourceRequestId, DeliveryOutcome outcome, Integer deliveredQuantity,
            String notes) {
    }

    /**
     * SRS-SFL-S173-03: HSE sets which events are higher-risk. Every field is optional; an absent one leaves
     * the current value in force.
     *
     * @param siteCode the site the values apply to; {@code null} for the platform default
     */
    public record ConfigureRiskCriteria(String siteCode, Integer attendanceThreshold,
            Boolean externalContractorsAreHigherRisk, Boolean temporaryStructuresAreHigherRisk,
            Set<String> higherRiskCategories, ActorContext actor, SourceChannel channel) {
    }
}
