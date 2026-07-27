package gh.edu.clet.sfl.fleetlogistics.dispatch.domain.policy;

/**
 * Pure closure-gating rule. A dispatch/custody chain cannot close while an open exception exists or a
 * custody gap is unresolved (SRS-SFL-S171-02/03/06: gaps, variances and discrepancies block closure).
 */
public final class DispatchClosurePolicy {
    private DispatchClosurePolicy() {}

    public static void requireClosable(boolean hasOpenException, boolean custodyClosable) {
        if (hasOpenException) {
            throw new IllegalStateException("Dispatch closure is blocked while an exception case is open");
        }
        if (!custodyClosable) {
            throw new IllegalStateException("Dispatch closure is blocked by an unresolved custody gap");
        }
    }
}
