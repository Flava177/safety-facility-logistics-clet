package gh.edu.clet.sfl.fleetlogistics.dispatch.domain.policy;

import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.ReturnReconciliation.ReturnOutcome;

/**
 * Pure return-leg reconciliation rules. Reconciles returned items against the original dispatch manifest;
 * a shortfall, extra or broken seal yields a DISCREPANCY that blocks custody closure.
 */
public final class ReturnReconciliationPolicy {
    private ReturnReconciliationPolicy() {}

    public record Result(ReturnOutcome outcome, int shortfall, int extras) {}

    public static Result evaluate(int expectedCount, int returnedCount, int brokenSeals) {
        int shortfall = Math.max(0, expectedCount - returnedCount);
        int extras = Math.max(0, returnedCount - expectedCount);
        ReturnOutcome outcome = (shortfall > 0 || extras > 0 || brokenSeals > 0)
                ? ReturnOutcome.DISCREPANCY : ReturnOutcome.MATCHED;
        return new Result(outcome, shortfall, extras);
    }
}
