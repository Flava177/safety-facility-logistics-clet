package gh.edu.clet.sfl.facilities.readiness.domain;

import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import java.util.Objects;
import java.util.UUID;

/**
 * One question a readiness assessment asks.
 *
 * <p>{@code severityIfFailed} is what turns a checklist into a control rather than a survey: the item
 * declares, ahead of any assessment, how bad it is when it fails. An assessor records pass or fail;
 * they do not get to decide how much the failure counts, which is what keeps two officers assessing
 * the same hall to the same standard.
 *
 * <p>{@code weight} contributes to the readiness score only. A zero-weight item still raises its
 * blocker — "does the fire door close" is pass/fail, not a percentage.
 */
public record ReadinessChecklistItem(
        UUID id,
        UUID checklistId,
        String itemCode,
        String description,
        BlockerSeverity severityIfFailed,
        boolean mandatory,
        int weight,
        int sortOrder) {

    public ReadinessChecklistItem {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(checklistId, "checklistId is required");
        EstateCodes.require(itemCode, "itemCode");
        EstateCodes.require(description, "description");
        Objects.requireNonNull(severityIfFailed, "severityIfFailed is required");
        if (weight < 0) {
            throw new IllegalArgumentException("weight cannot be negative");
        }
    }

    public static ReadinessChecklistItem of(UUID checklistId, String itemCode, String description,
            BlockerSeverity severityIfFailed, boolean mandatory, Integer weight, int sortOrder) {
        return new ReadinessChecklistItem(UUID.randomUUID(), checklistId, EstateCodes.normalize(itemCode),
                description.strip(), severityIfFailed == null ? BlockerSeverity.MINOR : severityIfFailed,
                mandatory, weight == null ? 1 : weight, sortOrder);
    }
}
