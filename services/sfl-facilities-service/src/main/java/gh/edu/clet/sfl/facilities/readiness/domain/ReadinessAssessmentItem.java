package gh.edu.clet.sfl.facilities.readiness.domain;

import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import java.util.Objects;
import java.util.UUID;

/**
 * One answer within an assessment.
 *
 * <p>Carries {@code itemCode}, {@code description} and {@code severityIfFailed} copied from the
 * checklist item rather than only its id. The checklist is versioned and will change; an assessment
 * has to remain readable against the question that was actually asked, and a foreign key alone would
 * make last month's result render against this month's wording.
 */
public record ReadinessAssessmentItem(
        UUID id,
        UUID assessmentId,
        UUID checklistItemId,
        String itemCode,
        String description,
        BlockerSeverity severityIfFailed,
        boolean mandatory,
        int weight,
        boolean passed,
        String comment) {

    public ReadinessAssessmentItem {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(assessmentId, "assessmentId is required");
        EstateCodes.require(itemCode, "itemCode");
        EstateCodes.require(description, "description");
        Objects.requireNonNull(severityIfFailed, "severityIfFailed is required");
    }

    /** Snapshots a checklist item together with the assessor's answer. */
    public static ReadinessAssessmentItem answered(UUID assessmentId, ReadinessChecklistItem item, boolean passed,
            String comment) {
        return new ReadinessAssessmentItem(UUID.randomUUID(), assessmentId, item.id(), item.itemCode(),
                item.description(), item.severityIfFailed(), item.mandatory(), item.weight(), passed,
                EstateCodes.blankToNull(comment));
    }

    /** The weight this answer contributes to the score. */
    public int earnedWeight() {
        return passed ? weight : 0;
    }
}
