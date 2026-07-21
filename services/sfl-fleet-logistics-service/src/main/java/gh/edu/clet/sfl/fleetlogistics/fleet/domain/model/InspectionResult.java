package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

/** Outcome of an inspection. */
public enum InspectionResult {
    PASSED,
    PASSED_WITH_DEFECTS,
    FAILED;

    /** Whether this result satisfies the "a valid inspection exists" readiness requirement. */
    public boolean satisfiesReadiness() {
        return this != FAILED;
    }
}
