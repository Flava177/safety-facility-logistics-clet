package gh.edu.clet.sfl.facilities.buildingsystems.domain;

/**
 * What a threshold rule tests - SRS-SFL-S156-02: "temperature band, power failure, lift fault code,
 * generator failed-start".
 *
 * <p>The three band conditions reduce to an upper limit, a lower limit or both, which is what lets two
 * overlapping rules be compared for strictness (the Rule Conflict error state). {@link #CODE_MATCH} is the
 * fault-code, lift-code and run-state case: a set of values that are each a fault, where "stricter" has no
 * meaning and so no conflict is possible.
 */
public enum RuleCondition {
    /** Breach when value &gt; upperLimit. */
    ABOVE(false, true),
    /** Breach when value &lt; lowerLimit. */
    BELOW(true, false),
    /** Breach when value &lt; lowerLimit or value &gt; upperLimit. */
    OUTSIDE_BAND(true, true),
    /** Breach when the value is one of the listed codes - a power-state 0, a lift fault code, a generator -1. */
    CODE_MATCH(false, false);

    private final boolean usesLower;
    private final boolean usesUpper;

    RuleCondition(boolean usesLower, boolean usesUpper) {
        this.usesLower = usesLower;
        this.usesUpper = usesUpper;
    }

    public boolean usesLower() {
        return usesLower;
    }

    public boolean usesUpper() {
        return usesUpper;
    }
}
