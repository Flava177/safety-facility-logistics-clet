package gh.edu.clet.sfl.facilities.buildingsystems.domain.policy;

import gh.edu.clet.sfl.facilities.buildingsystems.domain.AlertType;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BuildingSystemType;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.MeasuredQuantity;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.RuleCondition;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.ThresholdRule;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Which rule a reading breaches, and which rules conflict - SRS-SFL-S156-02.
 *
 * <h2>The stricter rule wins</h2>
 *
 * <p>The Rule Conflict error state settles what happens when two active rules cover the same sensor with
 * different thresholds: the stricter one applies. Every band rule reduces to an upper limit, a lower limit
 * or both, so "stricter" is well defined per side - the lowest upper limit and the highest lower limit
 * among the rules that apply. Evaluation takes those two effective limits and nothing else, which means a
 * looser rule can never mask a tighter one, whichever was written first.
 *
 * <p>Code-match rules (fault codes, lift codes, generator run state) have no strictness to compare. Each
 * is evaluated on its own, and none can conflict with another.
 *
 * <h2>What "applies" means</h2>
 *
 * <p>Same quantity, currently active (enabled and not superseded), and every scope field the rule sets -
 * system type, device, building, room - equal to the device's. Null scope fields match anything.
 */
public final class RuleEvaluationPolicy {

    private RuleEvaluationPolicy() {
    }

    /** The facts about a device a rule's scope can refer to. */
    public record DeviceFacts(UUID deviceId, BuildingSystemType systemType, String buildingCode, UUID roomId) {
    }

    /** A breach: which rule, which kind of alert it would be, and a sentence for the work order. */
    public record Breach(ThresholdRule rule, AlertType alertType, String description) {
    }

    /** Two rules that overlap for the same sensor with different thresholds, and which one wins. */
    public record Conflict(ThresholdRule first, ThresholdRule second, ThresholdRule stricter, String detail) {
    }

    public static boolean applies(ThresholdRule rule, DeviceFacts device, MeasuredQuantity quantity) {
        return rule.isActive()
                && rule.quantity() == quantity
                && (rule.systemType() == null || rule.systemType() == device.systemType())
                && (rule.deviceId() == null || rule.deviceId().equals(device.deviceId()))
                && (rule.buildingCode() == null || rule.buildingCode().equals(device.buildingCode()))
                && (rule.roomId() == null || rule.roomId().equals(device.roomId()));
    }

    /**
     * The breach a reading represents, if any.
     *
     * <p>When more than one kind of breach is present - a code match and a band breach on the same
     * reading - the one with the higher suggested priority is reported, so the work order carries the more
     * urgent framing.
     */
    public static Optional<Breach> evaluate(DeviceFacts device, MeasuredQuantity quantity, BigDecimal value,
            List<ThresholdRule> rules) {
        Objects.requireNonNull(value, "value is required");
        List<ThresholdRule> applicable = rules.stream().filter(rule -> applies(rule, device, quantity)).toList();
        List<Breach> breaches = new ArrayList<>();

        applicable.stream()
                .filter(rule -> rule.condition().usesUpper())
                .min(Comparator.comparing(ThresholdRule::upperLimit))
                .filter(strictest -> value.compareTo(strictest.upperLimit()) > 0)
                .ifPresent(strictest -> breaches.add(new Breach(strictest, AlertType.THRESHOLD_BREACH,
                        quantity + " " + value.toPlainString() + " is above the limit of "
                                + strictest.upperLimit().toPlainString() + " (rule '" + strictest.name() + "' v"
                                + strictest.ruleVersion() + ")")));
        applicable.stream()
                .filter(rule -> rule.condition().usesLower())
                .max(Comparator.comparing(ThresholdRule::lowerLimit))
                .filter(strictest -> value.compareTo(strictest.lowerLimit()) < 0)
                .ifPresent(strictest -> breaches.add(new Breach(strictest, AlertType.THRESHOLD_BREACH,
                        quantity + " " + value.toPlainString() + " is below the limit of "
                                + strictest.lowerLimit().toPlainString() + " (rule '" + strictest.name() + "' v"
                                + strictest.ruleVersion() + ")")));
        applicable.stream()
                .filter(rule -> rule.condition() == RuleCondition.CODE_MATCH)
                .filter(rule -> matchesCode(rule, value))
                .forEach(rule -> breaches.add(new Breach(rule, AlertType.FAULT_CODE,
                        quantity + " reported code " + value.toPlainString() + " (rule '" + rule.name() + "' v"
                                + rule.ruleVersion() + ")")));

        return breaches.stream().max(Comparator.comparing((Breach breach) -> breach.rule().priority()));
    }

    /**
     * The conflicts a rule has with the other active rules - the Rule Conflict error state.
     *
     * <p>Two band rules conflict when their scopes can both apply to one sensor (every scope field either
     * unset on one side or equal on both) and they set different limits on the same side. The scope test
     * is deliberately "could overlap" rather than "does today": a building-wide rule and a device rule in
     * that building overlap the moment the device reports, and a reviewer wants to know before then.
     */
    public static List<Conflict> conflictsOf(ThresholdRule candidate, List<ThresholdRule> others) {
        if (!candidate.isActive() || candidate.condition() == RuleCondition.CODE_MATCH) {
            return List.of();
        }
        List<Conflict> conflicts = new ArrayList<>();
        for (ThresholdRule other : others) {
            if (other.ruleId().equals(candidate.ruleId()) || !other.isActive()
                    || other.condition() == RuleCondition.CODE_MATCH || other.quantity() != candidate.quantity()
                    || !scopesOverlap(candidate, other)) {
                continue;
            }
            if (candidate.condition().usesUpper() && other.condition().usesUpper()
                    && candidate.upperLimit().compareTo(other.upperLimit()) != 0) {
                ThresholdRule stricter = candidate.upperLimit().compareTo(other.upperLimit()) < 0 ? candidate : other;
                conflicts.add(new Conflict(candidate, other, stricter, "Upper limits "
                        + candidate.upperLimit().toPlainString() + " and " + other.upperLimit().toPlainString()
                        + " overlap on " + candidate.quantity() + "; the stricter limit of "
                        + stricter.upperLimit().toPlainString() + " applies"));
            } else if (candidate.condition().usesLower() && other.condition().usesLower()
                    && candidate.lowerLimit().compareTo(other.lowerLimit()) != 0) {
                ThresholdRule stricter = candidate.lowerLimit().compareTo(other.lowerLimit()) > 0 ? candidate : other;
                conflicts.add(new Conflict(candidate, other, stricter, "Lower limits "
                        + candidate.lowerLimit().toPlainString() + " and " + other.lowerLimit().toPlainString()
                        + " overlap on " + candidate.quantity() + "; the stricter limit of "
                        + stricter.lowerLimit().toPlainString() + " applies"));
            }
        }
        return conflicts;
    }

    static boolean scopesOverlap(ThresholdRule left, ThresholdRule right) {
        return compatible(left.systemType(), right.systemType())
                && compatible(left.deviceId(), right.deviceId())
                && compatible(left.buildingCode(), right.buildingCode())
                && compatible(left.roomId(), right.roomId());
    }

    private static boolean compatible(Object left, Object right) {
        return left == null || right == null || left.equals(right);
    }

    private static boolean matchesCode(ThresholdRule rule, BigDecimal value) {
        try {
            return rule.codes().contains(value.intValueExact());
        } catch (ArithmeticException notAnInteger) {
            return false;
        }
    }
}
