package gh.edu.clet.sfl.facilities.buildingsystems.domain;

import java.util.Collection;

/**
 * A device's, system's, building's or site's health - SRS-SFL-S156-03: "normal, degraded, fault, offline",
 * plus {@link #UNKNOWN}, which the validation rule makes mandatory: "an explicit unknown/stale state is
 * always shown rather than defaulting to green".
 *
 * <p>The rollup takes the worst member, and the order below is the definition of worst. {@code UNKNOWN}
 * ranks above {@code NORMAL} so that a building with one silent sensor is never reported as healthy - the
 * failure mode where a dead sensor reads green is exactly what the SRS forbids.
 */
public enum HealthState {
    NORMAL(0),
    UNKNOWN(1),
    DEGRADED(2),
    OFFLINE(3),
    FAULT(4);

    private final int severity;

    HealthState(int severity) {
        this.severity = severity;
    }

    public int severity() {
        return severity;
    }

    /**
     * The worst of the given states. An empty collection is {@code UNKNOWN}, never {@code NORMAL}: nothing
     * reporting is not the same as everything fine.
     */
    public static HealthState worstOf(Collection<HealthState> states) {
        return states.stream()
                .reduce((left, right) -> left.severity >= right.severity ? left : right)
                .orElse(UNKNOWN);
    }
}
