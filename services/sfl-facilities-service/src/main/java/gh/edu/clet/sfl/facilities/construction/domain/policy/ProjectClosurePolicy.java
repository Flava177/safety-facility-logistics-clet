package gh.edu.clet.sfl.facilities.construction.domain.policy;

import gh.edu.clet.sfl.facilities.construction.domain.ConstructionProject;
import gh.edu.clet.sfl.facilities.construction.domain.DefectItem;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectStatus;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import java.time.LocalDate;
import java.util.List;

/**
 * Whether a handed-over project may close - SRS-SFL-S176-04.
 *
 * <p>"The project cannot close until all defects-liability items are closed or explicitly deferred
 * with reason." An OPEN defect - including one whose work order was cancelled rather than closed -
 * refuses the closure with {@code PROJECT_DEFECTS_OPEN}, naming every open item.
 *
 * <p>The defects-liability period must also have ended. The SRS workflow tracks the period and closes
 * "when liability items are resolved"; closing mid-period would stop defects being raised against the
 * contractor while the contractor is still liable. That is an interpretation, recorded in the gap
 * report; the refusal is a plain invalid transition naming the end date, not a defects error.
 */
public final class ProjectClosurePolicy {

    private ProjectClosurePolicy() {
    }

    public record Decision(boolean allowed, FacilitiesErrorCode refusal, String reason) {
    }

    public static Decision evaluate(ConstructionProject project, List<DefectItem> defects, LocalDate today) {
        if (project.status() != ProjectStatus.HANDED_OVER) {
            return new Decision(false, FacilitiesErrorCode.INVALID_STATE_TRANSITION,
                    "Only a handed-over project can close; " + project.projectReference() + " is "
                            + project.status() + ".");
        }
        List<String> open = defects.stream().filter(DefectItem::isOpen).map(DefectItem::defectReference).sorted()
                .toList();
        if (!open.isEmpty()) {
            return new Decision(false, FacilitiesErrorCode.PROJECT_DEFECTS_OPEN,
                    "Open defects-liability item(s): " + String.join(", ", open) + ".");
        }
        if (project.defectsLiabilityEndsOn() != null && !today.isAfter(project.defectsLiabilityEndsOn())) {
            return new Decision(false, FacilitiesErrorCode.INVALID_STATE_TRANSITION,
                    "The defects-liability period runs until " + project.defectsLiabilityEndsOn()
                            + "; the project closes after it ends.");
        }
        return new Decision(true, null, null);
    }
}
