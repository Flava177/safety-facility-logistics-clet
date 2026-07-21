package gh.edu.clet.sfl.fleetlogistics.fleet.api.request;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowPriority;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Request bodies for the fleet workflow endpoints (SRS-SFL-S166-02). */
public final class FleetWorkflowRequests {

    private FleetWorkflowRequests() {
    }

    /** {@code POST /api/v1/fleet/workflow-items}. */
    public record RaiseItem(
            @NotNull FleetWorkflowType workflowType,
            @Size(max = 80) String relatedRecordType,
            @Size(max = 160) String relatedRecordId,
            @NotBlank @Size(max = 40) String siteCode,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 2000) String description,
            @NotNull WorkflowPriority priority,
            @NotNull WorkflowSeverity severity,
            OperatingMode operatingMode,
            @Size(max = 160) String assignee) {
    }

    /** {@code PATCH /api/v1/fleet/workflow-items/{itemId}/assignment}. */
    public record AssignItem(
            @NotBlank @Size(max = 160) String assignee,
            @Size(max = 1000) String reason,
            Long expectedVersion) {
    }

    /** {@code PATCH /api/v1/fleet/workflow-items/{itemId}/progress}. */
    public record StartItem(Long expectedVersion) {
    }

    /** {@code PATCH /api/v1/fleet/workflow-items/{itemId}/hold}. */
    public record HoldItem(
            boolean resume,
            @Size(max = 1000) String reason,
            Long expectedVersion) {
    }

    /** {@code PATCH /api/v1/fleet/workflow-items/{itemId}/escalation}. Privileged. */
    public record EscalateItem(
            @NotBlank @Size(max = 1000) String reason,
            Long expectedVersion) {
    }

    /** {@code PATCH /api/v1/fleet/workflow-items/{itemId}/cancel}. Privileged. */
    public record CancelItem(
            @NotBlank @Size(max = 1000) String reason,
            Long expectedVersion) {
    }

    /** {@code PATCH /api/v1/fleet/workflow-items/{itemId}/closure}. Reason and evidence are mandatory. */
    public record CloseItem(
            @NotBlank @Size(max = 1000) String closureReason,
            @NotNull UUID closureEvidenceId,
            Long expectedVersion) {
    }

    /** {@code PATCH /api/v1/fleet/workflow-items/{itemId}/reopen}. Privileged. */
    public record ReopenItem(
            @NotBlank @Size(max = 1000) String reason,
            Long expectedVersion) {
    }

    /** {@code POST /api/v1/fleet/workflow-items/{itemId}/comments}. */
    public record AddComment(@NotBlank @Size(max = 4000) String body) {
    }
}
