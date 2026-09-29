package gh.edu.clet.sfl.facilities.buildingsystems.domain;

/**
 * The priority a rule suggests for the work order it raises - SRS-SFL-S156-02: "pre-populated with the
 * system, location, telemetry evidence and suggested priority".
 *
 * <p>S156's own enum rather than S153's {@code FaultPriority}: the domain does not reach into another
 * module. The names match one for one and the S153 adapter maps by name, so a new S153 priority fails
 * loudly in that adapter rather than being guessed here. It is a suggestion - S153 triage can move it.
 */
public enum AlertPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public boolean atLeast(AlertPriority threshold) {
        return threshold != null && ordinal() >= threshold.ordinal();
    }
}
