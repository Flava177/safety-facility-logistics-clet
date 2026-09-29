package gh.edu.clet.sfl.facilities.construction.domain;

import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The life of a construction project - SRS-SFL-S176-01 and -04.
 *
 * <p>The SRS workflow is "registered, scope/budget/milestones defined, approval recorded, permits
 * linked where required, in progress", then "works complete, handover recorded, defects-liability
 * period tracked, closed". Each state below is one of those steps, and the table is explicit so the
 * transitions a reviewer has to trust are written down in one place rather than scattered across
 * {@code if} statements.
 *
 * <h2>Why PROPOSED exists</h2>
 *
 * An approved S158 space-change request becomes an S176 project reference the moment it is actioned
 * (SRS-SFL-S158-04 acceptance criterion), long before anybody has a budget for it. PROPOSED is that
 * reference: it has a scope and an origin and nothing else. Registering it - baseline, funding source,
 * milestones, work types, a project manager - is what moves it to REGISTERED, so the intake never
 * shortcuts a gate: every project, however it arrived, is approved from REGISTERED.
 *
 * <h2>Why APPROVED can fall back to REGISTERED</h2>
 *
 * The accountable approver signed a specific baseline. Revising the baseline afterwards means the
 * sign-off no longer covers what the project now says it will cost, so the project returns to
 * REGISTERED for a fresh sign-off instead of starting on an approval of a different number.
 *
 * <h2>What is deliberately missing</h2>
 *
 * REGISTERED to IN_PROGRESS. Starting without a sign-off is the first error state S176-01 names
 * ("Missing Approval"); the service answers it with {@code PROJECT_APPROVAL_MISSING} rather than a
 * generic invalid transition, and {@code ck_construction_projects_approved_before_start} in V21
 * refuses it at the database. And nothing leaves HANDED_OVER except CLOSED: once the space is in the
 * operational register, cancelling the project would not take it out again.
 */
public enum ProjectStatus {

    /** Created from an S158 space-change request; scope and origin only. */
    PROPOSED,
    /** Scope, baseline, funding source, milestones, work types and project manager are defined. */
    REGISTERED,
    /** Signed off by an accountable approver for the current baseline. */
    APPROVED,
    /** Works have started - past the approval and permit gate. */
    IN_PROGRESS,
    /** The works are complete; the space is not yet in operational use. */
    PRACTICAL_COMPLETION,
    /** In the S152 register and in use; the defects-liability period is running. */
    HANDED_OVER,
    /** Every defects-liability item closed or deferred with a reason. Terminal. */
    CLOSED,
    /** Abandoned before handover, with a reason. Terminal. */
    CANCELLED;

    private static final Map<ProjectStatus, Set<ProjectStatus>> ALLOWED = Map.of(
            PROPOSED, EnumSet.of(REGISTERED, CANCELLED),
            REGISTERED, EnumSet.of(APPROVED, CANCELLED),
            APPROVED, EnumSet.of(IN_PROGRESS, REGISTERED, CANCELLED),
            IN_PROGRESS, EnumSet.of(PRACTICAL_COMPLETION, CANCELLED),
            PRACTICAL_COMPLETION, EnumSet.of(HANDED_OVER),
            HANDED_OVER, EnumSet.of(CLOSED),
            CLOSED, EnumSet.noneOf(ProjectStatus.class),
            CANCELLED, EnumSet.noneOf(ProjectStatus.class));

    /** The table itself, for the transition-table test and for documentation. */
    public static Set<ProjectStatus> allowedFrom(ProjectStatus from) {
        return Set.copyOf(ALLOWED.getOrDefault(from, Set.of()));
    }

    public boolean canTransitionTo(ProjectStatus target) {
        return target != null && ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public ProjectStatus transitionTo(ProjectStatus target) {
        if (!canTransitionTo(target)) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "A construction project cannot move from " + name() + " to " + target + ".");
        }
        return target;
    }

    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }

    /** Before works start: the baseline may still be revised, and doing so needs a fresh sign-off. */
    public boolean isPreStart() {
        return this == PROPOSED || this == REGISTERED || this == APPROVED;
    }

    /**
     * Whether a variation order may be raised against the project.
     *
     * <p>From approval until the works are complete. Before approval there is no approved baseline to
     * vary against - the baseline itself is still being revised - and after practical completion a
     * change to the works is a defect or a new project, not a variation.
     */
    public boolean acceptsVariations() {
        return this == APPROVED || this == IN_PROGRESS || this == PRACTICAL_COMPLETION;
    }
}
