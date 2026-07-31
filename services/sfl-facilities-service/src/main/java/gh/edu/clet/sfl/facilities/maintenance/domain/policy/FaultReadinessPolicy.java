package gh.edu.clet.sfl.facilities.maintenance.domain.policy;

import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFault;
import gh.edu.clet.sfl.facilities.maintenance.domain.FaultPriority;
import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSeverity;

/**
 * What a reported fault does to the readiness of the space it was reported in.
 *
 * <p>This is the join S153 exists to make. A fault is not only a ticket: if it affects a space, it
 * affects whether that space can be used, and until now the two facts lived in different modules
 * with nothing between them — an examination hall could be flooded and still read as READY.
 *
 * <h2>Why the mapping mirrors the asset one rather than inventing a second</h2>
 *
 * S152 already maps an impaired asset to a blocker severity in
 * {@code ReadinessApplicationService.severityFor}: criticality sets the ceiling, status sets how much
 * of it applies. Faults are the same shape with priority in place of criticality, so the mapping is
 * the same shape too. Two different ladders would mean a generator failure and a fault reported
 * against that same generator could produce different severities for one physical problem, and
 * nobody reading the space would be able to say which was right.
 *
 * <p>The threshold is configurable rather than fixed at HIGH, because what counts as "stops the hall
 * being used" is a centre's judgement, not a developer's. It is passed in rather than read here —
 * this class stays a pure function so the rule is testable without a configuration store.
 */
public final class FaultReadinessPolicy {

    private FaultReadinessPolicy() {
    }

    /**
     * The blocker severity a fault earns, or {@code null} when it should raise none.
     *
     * <p>{@code null} for three reasons, each of which is a real case rather than a guard:
     * the fault is not in a room the estate knows about; it is below the site's threshold; or it is
     * no longer open, in which case any blocker it held should be resolved rather than re-raised.
     */
    public static BlockerSeverity severityFor(FacilityFault fault, FaultPriority blockerThreshold) {
        if (fault == null || fault.roomId() == null || !fault.status().isOpen()) {
            return null;
        }
        if (!fault.priority().atLeast(blockerThreshold)) {
            return null;
        }
        return switch (fault.priority()) {
            case CRITICAL -> BlockerSeverity.CRITICAL;
            case HIGH -> BlockerSeverity.MAJOR;
            case MEDIUM -> BlockerSeverity.MINOR;
            case LOW -> BlockerSeverity.ADVISORY;
        };
    }

    /** The blocker description, phrased so a reader of the space knows what to chase. */
    public static String describe(FacilityFault fault) {
        return fault.faultNumber() + ": " + fault.title();
    }

    /** The resolution note left on the blocker when the fault is dealt with. */
    public static String resolution(FacilityFault fault) {
        return "Fault " + fault.faultNumber() + " is " + fault.status()
                + (fault.workOrderId() == null ? "." : " (work order " + fault.workOrderId() + ").");
    }

    /** The source reference a blocker carries, so reconciliation can find its own blockers again. */
    public static String reference(FacilityFault fault) {
        return fault.id().toString();
    }
}
