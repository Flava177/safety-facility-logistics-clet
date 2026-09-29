package gh.edu.clet.sfl.facilities.spaceplanning.domain;

/**
 * The computed result of checking a space's allocation against its occupancy standard - SRS-SFL-S158-02.
 *
 * <p>Three values, not two, because the SRS's own error state requires it: "Standard Not Defined - a
 * space type with no configured standard; compliance shown as not-evaluated, not as compliant by
 * default." A two-valued status would have to call an unchecked room one or the other, and either lie
 * is worse than admitting nobody checked.
 */
public enum ComplianceStatus {
    COMPLIANT,
    NON_COMPLIANT,
    /** No standard for the space type, or the room lacks the capacity/area the standard is written in. */
    NOT_EVALUATED
}
