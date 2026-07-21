package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import gh.edu.clet.sfl.common.security.SflRole;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * The SLA an item is held to, resolved from the runtime configuration active at evaluation time
 * (SRS-SFL-S166-02).
 *
 * @param escalationRole the role notified when the item breaches; part of the rule, not a constant, so
 *        a site can escalate to a different role without a code change
 */
public record SlaTarget(
        Duration responseTarget,
        Duration resolutionTarget,
        SflRole escalationRole,
        String ruleReference) {

    public SlaTarget {
        Objects.requireNonNull(responseTarget, "responseTarget is required");
        Objects.requireNonNull(resolutionTarget, "resolutionTarget is required");
        Objects.requireNonNull(escalationRole, "escalationRole is required");
        if (responseTarget.isNegative() || resolutionTarget.isNegative()) {
            throw new IllegalArgumentException("SLA targets cannot be negative");
        }
        if (resolutionTarget.compareTo(responseTarget) < 0) {
            throw new IllegalArgumentException("resolutionTarget cannot be shorter than responseTarget");
        }
        ruleReference = ruleReference == null || ruleReference.isBlank() ? "default" : ruleReference.strip();
    }

    /** When resolution is due, given when the item was raised. */
    public Instant dueAt(Instant raisedAt) {
        return raisedAt.plus(resolutionTarget);
    }

    /** When first response is due, given when the item was raised. */
    public Instant responseDueAt(Instant raisedAt) {
        return raisedAt.plus(responseTarget);
    }
}
