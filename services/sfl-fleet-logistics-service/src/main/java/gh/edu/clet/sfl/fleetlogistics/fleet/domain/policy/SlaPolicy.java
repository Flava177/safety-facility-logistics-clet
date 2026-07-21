package gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy;

import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SlaTarget;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowPriority;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowSeverity;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves the SLA for a workflow item from the configured rules
 * (SRS-SFL-S166-02: "The system shall calculate SLA timers from configurable priority, severity, site,
 * operating mode and workflow type rules").
 *
 * <p>Rules are matched most-specific-first: a rule naming all five dimensions beats one naming three,
 * so a site can add a local exception without restating the whole table. When nothing matches, the
 * compiled-in default applies and says so through {@code ruleReference}, which makes an unconfigured
 * environment visible on the item itself rather than silently generous.
 */
public final class SlaPolicy {

    /** Applied when no configured rule matches. Deliberately tight enough to be noticed. */
    private static final Duration DEFAULT_RESPONSE = Duration.ofHours(4);
    private static final Duration DEFAULT_RESOLUTION = Duration.ofHours(24);
    private static final SflRole DEFAULT_ESCALATION_ROLE = SflRole.FLEET_MANAGER;
    static final String DEFAULT_RULE_REFERENCE = "compiled-in-default";

    private SlaPolicy() {
    }

    /**
     * Picks the SLA target for an item.
     *
     * @param rules every currently effective rule, read at evaluation time so escalation always uses
     *        the configuration active now (SRS-SFL-S166-02 validation rule)
     */
    public static SlaTarget resolve(List<SlaRule> rules, FleetWorkflowType workflowType,
            WorkflowPriority priority, WorkflowSeverity severity, String siteCode, OperatingMode operatingMode) {
        Optional<SlaRule> match = (rules == null ? List.<SlaRule>of() : rules).stream()
                .filter(rule -> rule.matches(workflowType, priority, severity, siteCode, operatingMode))
                .max(Comparator.comparingInt(SlaRule::specificity));

        return match
                .map(rule -> new SlaTarget(rule.responseTarget(), rule.resolutionTarget(), rule.escalationRole(),
                        rule.reference()))
                .orElseGet(() -> new SlaTarget(DEFAULT_RESPONSE, DEFAULT_RESOLUTION, DEFAULT_ESCALATION_ROLE,
                        DEFAULT_RULE_REFERENCE));
    }

    /**
     * One configured SLA rule. A {@code null} dimension means "any", which is how a broad default and a
     * narrow exception coexist in the same table.
     */
    public record SlaRule(
            String reference,
            FleetWorkflowType workflowType,
            WorkflowPriority priority,
            WorkflowSeverity severity,
            String siteCode,
            OperatingMode operatingMode,
            Duration responseTarget,
            Duration resolutionTarget,
            SflRole escalationRole) {

        public SlaRule {
            Objects.requireNonNull(responseTarget, "responseTarget is required");
            Objects.requireNonNull(resolutionTarget, "resolutionTarget is required");
            Objects.requireNonNull(escalationRole, "escalationRole is required");
            reference = reference == null || reference.isBlank() ? "unnamed-rule" : reference.strip();
        }

        boolean matches(FleetWorkflowType type, WorkflowPriority requestedPriority,
                WorkflowSeverity requestedSeverity, String requestedSite, OperatingMode requestedMode) {
            return (workflowType == null || workflowType == type)
                    && (priority == null || priority == requestedPriority)
                    && (severity == null || severity == requestedSeverity)
                    && (siteCode == null || siteCode.equalsIgnoreCase(requestedSite))
                    && (operatingMode == null || operatingMode == requestedMode);
        }

        /** How many dimensions this rule pins down; higher wins. */
        int specificity() {
            int score = 0;
            if (workflowType != null) {
                score++;
            }
            if (priority != null) {
                score++;
            }
            if (severity != null) {
                score++;
            }
            if (siteCode != null) {
                score++;
            }
            if (operatingMode != null) {
                score++;
            }
            return score;
        }
    }
}
