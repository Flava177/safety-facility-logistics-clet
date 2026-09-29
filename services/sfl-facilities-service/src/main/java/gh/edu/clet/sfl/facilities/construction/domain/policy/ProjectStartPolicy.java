package gh.edu.clet.sfl.facilities.construction.domain.policy;

import gh.edu.clet.sfl.facilities.construction.domain.ConstructionProject;
import gh.edu.clet.sfl.facilities.construction.domain.PermitRecord;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectApproval;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectPermitLink;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectStatus;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The gate between approved and in progress - SRS-SFL-S176-01.
 *
 * <p>"A project cannot be marked in progress without an approval record and at least one linked
 * Permit-to-Work (S164) where the work type requires one." Two checks, in that order, because each
 * has its own error state and a project manager told "no permit" when the real problem is "nobody has
 * signed this off" would chase the wrong person.
 *
 * <h2>Stricter than "at least one" on purpose</h2>
 *
 * A project declaring both HOT_WORK and CONFINED_SPACE needs a current permit <em>for each</em>. One
 * hot-work permit does not authorise anybody to enter a tank; reading the SRS's "at least one" as one
 * per project would let the first permit cover every other hazard on the job. Recorded in the S176 gap
 * report as an interpretation.
 *
 * <p>Pure: no clock, no repository. The caller supplies "now", the links and the projection rows.
 */
public final class ProjectStartPolicy {

    private ProjectStartPolicy() {
    }

    /** The outcome, with the SRS error code and the reason a project manager can act on. */
    public record Decision(boolean allowed, FacilitiesErrorCode refusal, String reason, Set<String> missingPermits) {

        static Decision allow() {
            return new Decision(true, null, null, Set.of());
        }

        static Decision refuse(FacilitiesErrorCode code, String reason, Set<String> missing) {
            return new Decision(false, code, reason, Set.copyOf(missing));
        }
    }

    public static Decision evaluate(ConstructionProject project, Optional<ProjectApproval> approval,
            Set<String> permitRequiredTypes, List<ProjectPermitLink> links, Map<String, PermitRecord> permits,
            Instant now) {
        if (project.status() == ProjectStatus.PROPOSED || project.status() == ProjectStatus.REGISTERED) {
            return Decision.refuse(FacilitiesErrorCode.PROJECT_APPROVAL_MISSING,
                    "Project " + project.projectReference() + " is " + project.status()
                            + " and has no recorded sign-off from an accountable approver.", Set.of());
        }
        if (project.status() == ProjectStatus.APPROVED
                && (approval.isEmpty() || !approval.get().covers(project))) {
            return Decision.refuse(FacilitiesErrorCode.PROJECT_APPROVAL_MISSING,
                    "No sign-off covers baseline revision " + project.baselineRevision() + " of project "
                            + project.projectReference() + ".", Set.of());
        }
        Set<String> missing = missingPermits(project.workTypes(), permitRequiredTypes, links, permits, now);
        if (!missing.isEmpty()) {
            return Decision.refuse(FacilitiesErrorCode.PROJECT_PERMIT_MISSING,
                    "No current linked S164 permit for work type(s) " + String.join(", ", missing) + ".", missing);
        }
        return Decision.allow();
    }

    /** The permit-requiring work types on the project with no current linked permit of that type. */
    public static Set<String> missingPermits(Collection<String> workTypes, Set<String> permitRequiredTypes,
            List<ProjectPermitLink> links, Map<String, PermitRecord> permits, Instant now) {
        Set<String> missing = new TreeSet<>();
        for (String type : workTypes) {
            if (!permitRequiredTypes.contains(type)) {
                continue;
            }
            boolean covered = links.stream()
                    .filter(link -> link.workType().equals(type))
                    .anyMatch(link -> link.isSatisfiedBy(permits.get(link.permitId()), now));
            if (!covered) {
                missing.add(type);
            }
        }
        return missing;
    }
}
