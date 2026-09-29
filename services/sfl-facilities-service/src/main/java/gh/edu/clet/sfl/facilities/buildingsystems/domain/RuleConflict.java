package gh.edu.clet.sfl.facilities.buildingsystems.domain;

import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A logged Rule Conflict - SRS-SFL-S156-02 error state: "overlapping active rules for the same sensor with
 * different thresholds; the stricter rule wins and the conflict is logged for review".
 *
 * <p>Logged, not refused. Two engineers writing overlapping rules is ordinary - a site-wide server-room
 * rule and a tighter one for the examination-paper store - and refusing the second would push somebody to
 * disable the first to make room. The stricter one is applied at evaluation time either way; this row is
 * what lets a reviewer notice that the looser one is dead weight.
 *
 * @param ruleId the rule whose create/revise/enable surfaced the conflict
 * @param winningRuleId the stricter of the two, which is the one evaluation applies
 */
public record RuleConflict(
        UUID id,
        String siteCode,
        UUID ruleId,
        UUID conflictingRuleId,
        UUID winningRuleId,
        MeasuredQuantity quantity,
        String detail,
        Instant detectedAt,
        RecordMetadata metadata) {

    public RuleConflict {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(ruleId, "ruleId is required");
        Objects.requireNonNull(conflictingRuleId, "conflictingRuleId is required");
        Objects.requireNonNull(winningRuleId, "winningRuleId is required");
        Objects.requireNonNull(metadata, "metadata is required");
    }
}
