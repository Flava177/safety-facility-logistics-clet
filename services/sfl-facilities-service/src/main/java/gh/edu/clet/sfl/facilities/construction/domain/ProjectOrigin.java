package gh.edu.clet.sfl.facilities.construction.domain;

/**
 * Where a project came from.
 *
 * <p>Recorded because it changes what handover owes: a project that arrived from an approved S158
 * space-change request may carry a committed scenario, and SRS-SFL-S176-04 requires handover to
 * confirm it. A directly registered project owes S158 nothing.
 */
public enum ProjectOrigin {
    /** Registered by a construction project manager. */
    DIRECT,
    /** Proposed through {@code ConstructionProjectIntake} from an approved S158 space-change request. */
    S158_SPACE_CHANGE
}
