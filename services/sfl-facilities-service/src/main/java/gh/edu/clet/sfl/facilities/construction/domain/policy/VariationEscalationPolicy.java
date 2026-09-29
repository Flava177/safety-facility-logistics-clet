package gh.edu.clet.sfl.facilities.construction.domain.policy;

import gh.edu.clet.sfl.facilities.construction.domain.VariationOrder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * When a variation needs escalated approval - SRS-SFL-S176-03.
 *
 * <p>"Cumulative variations beyond a configured percentage of original budget require escalated
 * approval", and the error state: "further variations blocked until obtained". Two separate triggers,
 * either of which holds a variation for the escalated authority:
 *
 * <ol>
 *   <li><strong>Crossing.</strong> Approving it would take the cumulative approved variation above the
 *       threshold. Measured on the <em>net</em> of approved deltas plus this one, against the baseline
 *       the accountable approver signed - the original budget, not the current one, so a project cannot
 *       creep past the line in steps that each look small against a budget that has already grown.
 *       Only a variation that adds cost can cross; a saving never needs more authority than the
 *       overrun it reduces. Once over the line, every further cost-adding variation crosses it again
 *       and is escalated in turn.</li>
 *   <li><strong>Blocked.</strong> Another variation on the same project is already waiting for
 *       escalated approval. Until that is obtained, nothing further - saving or not - is approved on
 *       ordinary authority, because the ordinary approver would be deciding against a budget position
 *       the escalated approver has not yet accepted.</li>
 * </ol>
 *
 * <p>Net rather than gross is a stated interpretation (S176 gap report): a +15% change offset by a
 * -10% one leaves the budget 5% over, and that is what the escalated approver is protecting.
 */
public final class VariationEscalationPolicy {

    private VariationEscalationPolicy() {
    }

    public record Assessment(boolean escalationRequired, boolean blocked, BigDecimal cumulativePercentAfter,
            String reason) {
    }

    /**
     * @param originalBaseline the baseline the project's approval covers
     * @param variations every variation on the project, this one included
     */
    public static Assessment assess(VariationOrder candidate, BigDecimal originalBaseline,
            Collection<VariationOrder> variations, BigDecimal thresholdPercent) {
        BigDecimal approved = approvedTotal(variations);
        BigDecimal after = approved.add(candidate.costDelta());
        BigDecimal percent = percentOf(after, originalBaseline);
        boolean crosses = candidate.costDelta().signum() > 0 && percent.compareTo(thresholdPercent) > 0;
        boolean blocked = variations.stream()
                .anyMatch(other -> !other.id().equals(candidate.id()) && other.awaitingEscalation());
        String reason = null;
        if (crosses) {
            reason = "Approving " + candidate.variationReference() + " would take cumulative approved variations to "
                    + percent.setScale(2, RoundingMode.HALF_UP).toPlainString() + "% of the original budget, above the "
                    + thresholdPercent.stripTrailingZeros().toPlainString() + "% threshold; escalated approval required.";
        } else if (blocked) {
            reason = "Another variation on this project is awaiting escalated approval; " + candidate.variationReference()
                    + " is held until it is obtained.";
        }
        return new Assessment(crosses || blocked, blocked, percent, reason);
    }

    /** Sum of approved deltas. Nothing else - S176-03's "until its own approval is recorded". */
    public static BigDecimal approvedTotal(Collection<VariationOrder> variations) {
        return variations.stream()
                .filter(variation -> variation.status() == VariationOrder.Status.APPROVED)
                .map(VariationOrder::costDelta)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static BigDecimal percentOf(BigDecimal amount, BigDecimal baseline) {
        if (baseline == null || baseline.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(BigDecimal.valueOf(100)).divide(baseline, 4, RoundingMode.HALF_UP);
    }
}
