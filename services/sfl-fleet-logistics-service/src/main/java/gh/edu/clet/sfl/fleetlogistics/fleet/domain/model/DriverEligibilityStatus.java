package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

/**
 * Whether a driver may be assigned (SRS-SFL-S166-05 "driver eligibility blockers").
 *
 * <p>Derived by {@code DriverEligibilityPolicy} from licence validity, licence class, medical
 * clearance and lifecycle status. {@link #CONDITIONAL} means only warning-level blockers are present —
 * the assignment may proceed but the dispatcher is told why to keep an eye on it.
 */
public enum DriverEligibilityStatus {
    ELIGIBLE,
    CONDITIONAL,
    INELIGIBLE,
    SUSPENDED;

    public boolean permitsAssignment() {
        return this == ELIGIBLE || this == CONDITIONAL;
    }
}
