package gh.edu.clet.sfl.fleetlogistics.dispatch.domain.policy;

import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchReceipt.ReceiptOutcome;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchReceipt.VarianceType;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.SealState;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure receipt-variance rules. Confirms a receipt against the manifest by seal integrity, item count and
 * recipient signature; any deviation is a variance that opens an exception. The stored variance type is
 * the most severe deviation; all deviations are retained as reasons.
 */
public final class ReceiptVariancePolicy {
    private ReceiptVariancePolicy() {}

    public record Result(ReceiptOutcome outcome, VarianceType type, List<String> reasons) {}

    public static Result evaluate(SealState sealState, int expectedCount, int verifiedCount, String recipientName,
            String expectedRecipient, boolean hasSignature) {
        List<String> reasons = new ArrayList<>();
        VarianceType type = null;
        if (sealState.isCompromised()) { reasons.add("BROKEN_SEAL(" + sealState + ")"); type = VarianceType.BROKEN_SEAL; }
        if (expectedRecipient != null && !expectedRecipient.isBlank()
                && !expectedRecipient.strip().equalsIgnoreCase(recipientName == null ? "" : recipientName.strip())) {
            reasons.add("WRONG_RECIPIENT(expected=" + expectedRecipient.strip() + ")");
            if (type == null) type = VarianceType.WRONG_RECIPIENT;
        }
        if (verifiedCount < expectedCount) {
            reasons.add("SHORT_COUNT(expected=" + expectedCount + ",verified=" + verifiedCount + ")");
            if (type == null) type = VarianceType.SHORT_COUNT;
        } else if (verifiedCount > expectedCount) {
            reasons.add("OVER_COUNT(expected=" + expectedCount + ",verified=" + verifiedCount + ")");
            if (type == null) type = VarianceType.OVER_COUNT;
        }
        if (!hasSignature) {
            reasons.add("MISSING_SIGNATURE");
            if (type == null) type = VarianceType.MISSING_SIGNATURE;
        }
        return type == null ? new Result(ReceiptOutcome.CLEAN, null, List.of())
                : new Result(ReceiptOutcome.VARIANCE, type, List.copyOf(reasons));
    }
}
