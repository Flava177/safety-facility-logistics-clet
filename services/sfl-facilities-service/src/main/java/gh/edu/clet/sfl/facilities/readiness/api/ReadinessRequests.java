package gh.edu.clet.sfl.facilities.readiness.api;

import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSeverity;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Request bodies for the readiness endpoints. */
public final class ReadinessRequests {

    private ReadinessRequests() {
    }

    public record CreateChecklist(
            @NotBlank @Size(max = 40) String siteCode,
            @NotBlank @Size(max = 60) String checklistCode,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            /** Null means the checklist applies to any space type. */
            SpaceType spaceType,
            /** Null means the checklist applies in any operating mode. */
            OperatingMode operatingMode,
            @NotEmpty @Valid List<ChecklistItem> items) {
    }

    public record ChecklistItem(
            @NotBlank @Size(max = 60) String itemCode,
            @NotBlank @Size(max = 500) String description,
            @NotNull BlockerSeverity severityIfFailed,
            Boolean mandatory,
            @Min(0) Integer weight,
            Integer sortOrder) {
    }

    public record UpdateChecklist(
            @Size(max = 200) String name,
            @Size(max = 1000) String description,
            /** Omit to leave the questions alone; supplying any replaces them all and bumps the version. */
            @Valid List<ChecklistItem> items,
            Long expectedVersion) {
    }

    public record SubmitAssessment(
            @NotNull UUID roomId,
            /** Omit to let the service resolve the checklist from the space type and operating mode. */
            UUID checklistId,
            @Valid List<AssessmentAnswer> answers,
            @Size(max = 2000) String notes) {
    }

    public record AssessmentAnswer(
            @NotBlank @Size(max = 60) String itemCode,
            boolean passed,
            @Size(max = 1000) String comment) {
    }

    public record RaiseBlocker(
            @NotNull UUID roomId,
            @NotNull BlockerSeverity severity,
            @NotBlank @Size(max = 1000) String description) {
    }

    public record ResolveBlocker(
            @NotBlank @Size(max = 1000) String resolutionNotes) {
    }

    public record ReadinessLock(
            @Size(max = 500) String reason) {
    }
}
