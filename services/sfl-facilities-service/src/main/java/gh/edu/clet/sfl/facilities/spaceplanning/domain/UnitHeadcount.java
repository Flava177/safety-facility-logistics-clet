package gh.edu.clet.sfl.facilities.spaceplanning.domain;

/**
 * One unit's planned headcount in one space.
 *
 * @param allocatedUnit the organisational unit. Keyed by the planner - HRMS (S140) is not available to
 *        SFL, so there is no unit list to validate against; see the S158 gap report
 */
public record UnitHeadcount(String allocatedUnit, int headcount) {

    public UnitHeadcount {
        if (allocatedUnit == null || allocatedUnit.isBlank()) {
            throw new IllegalArgumentException("allocatedUnit is required");
        }
        allocatedUnit = allocatedUnit.strip();
        if (allocatedUnit.length() > 200) {
            throw new IllegalArgumentException("allocatedUnit must be at most 200 characters");
        }
        if (headcount < 0) {
            throw new IllegalArgumentException("headcount cannot be negative");
        }
    }
}
