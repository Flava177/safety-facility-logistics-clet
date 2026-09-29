package gh.edu.clet.sfl.facilities.buildingsystems.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.facilities.buildingsystems.domain.AlertPriority;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BuildingSystemType;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.MeasuredQuantity;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.RuleCondition;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.ThresholdRule;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** SRS-SFL-S156-02: rule evaluation and the "stricter rule wins" Rule Conflict resolution. */
class RuleEvaluationPolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-28T08:00:00Z");
    private static final UUID DEVICE = UUID.randomUUID();
    private static final RuleEvaluationPolicy.DeviceFacts DEVICE_FACTS = new RuleEvaluationPolicy.DeviceFacts(DEVICE,
            BuildingSystemType.HVAC, "LAW", null);

    @Test
    void a_value_above_the_upper_limit_breaches() {
        ThresholdRule rule = above("LAW-A", new BigDecimal("28"));

        var breach = RuleEvaluationPolicy.evaluate(DEVICE_FACTS, MeasuredQuantity.TEMPERATURE_C,
                new BigDecimal("30"), List.of(rule));

        assertThat(breach).isPresent();
        assertThat(breach.get().rule()).isEqualTo(rule);
    }

    @Test
    void a_value_within_range_does_not_breach() {
        ThresholdRule rule = above("LAW-A", new BigDecimal("28"));

        var breach = RuleEvaluationPolicy.evaluate(DEVICE_FACTS, MeasuredQuantity.TEMPERATURE_C,
                new BigDecimal("22"), List.of(rule));

        assertThat(breach).isEmpty();
    }

    @Test
    void overlapping_rules_the_stricter_upper_limit_applies() {
        ThresholdRule looser = above("Estate-wide", new BigDecimal("30"));
        ThresholdRule stricter = above("Server room", new BigDecimal("24"));

        var breach = RuleEvaluationPolicy.evaluate(DEVICE_FACTS, MeasuredQuantity.TEMPERATURE_C,
                new BigDecimal("26"), List.of(looser, stricter));

        assertThat(breach).isPresent();
        assertThat(breach.get().rule()).isEqualTo(stricter);
    }

    @Test
    void the_conflict_between_two_overlapping_rules_is_detected_and_the_stricter_one_named() {
        ThresholdRule looser = above("Estate-wide", new BigDecimal("30"));
        ThresholdRule stricter = above("Server room", new BigDecimal("24"));

        List<RuleEvaluationPolicy.Conflict> conflicts = RuleEvaluationPolicy.conflictsOf(looser, List.of(stricter));

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get(0).stricter()).isEqualTo(stricter);
    }

    @Test
    void rules_with_the_same_limit_do_not_conflict() {
        ThresholdRule first = above("A", new BigDecimal("28"));
        ThresholdRule second = above("B", new BigDecimal("28"));

        assertThat(RuleEvaluationPolicy.conflictsOf(first, List.of(second))).isEmpty();
    }

    @Test
    void code_match_rules_never_conflict() {
        ThresholdRule fault = codeMatch(Set.of(3));

        assertThat(RuleEvaluationPolicy.conflictsOf(fault, List.of(fault))).isEmpty();
    }

    @Test
    void a_rule_scoped_to_a_different_device_does_not_apply() {
        ThresholdRule scoped = new ThresholdRule(UUID.randomUUID(), UUID.randomUUID(), 1, "MAIN", "Other device",
                MeasuredQuantity.TEMPERATURE_C, null, UUID.randomUUID(), null, null, RuleCondition.ABOVE, null,
                new BigDecimal("10"), Set.of(), Duration.ofMinutes(5), AlertPriority.MEDIUM, true, null, null, null,
                null, metadata());

        var breach = RuleEvaluationPolicy.evaluate(DEVICE_FACTS, MeasuredQuantity.TEMPERATURE_C,
                new BigDecimal("30"), List.of(scoped));

        assertThat(breach).isEmpty();
    }

    @Test
    void a_disabled_rule_never_breaches() {
        ThresholdRule disabled = above("Disabled", new BigDecimal("20")).disable(UUID.randomUUID(), "maintenance",
                "engineer.owner", "director", NOW, SourceChannel.WEB, "corr-1");

        var breach = RuleEvaluationPolicy.evaluate(DEVICE_FACTS, MeasuredQuantity.TEMPERATURE_C,
                new BigDecimal("30"), List.of(disabled));

        assertThat(breach).isEmpty();
    }

    private static ThresholdRule above(String name, BigDecimal upper) {
        return ThresholdRule.create(UUID.randomUUID(), UUID.randomUUID(), "MAIN", name, MeasuredQuantity.TEMPERATURE_C,
                BuildingSystemType.HVAC, null, "LAW", null, RuleCondition.ABOVE, null, upper, Set.of(),
                Duration.ofMinutes(5), AlertPriority.HIGH, "initial", "engineer", NOW, SourceChannel.WEB, "corr-1");
    }

    private static ThresholdRule codeMatch(Set<Integer> codes) {
        return ThresholdRule.create(UUID.randomUUID(), UUID.randomUUID(), "MAIN", "Lift fault", MeasuredQuantity.LIFT_STATUS,
                BuildingSystemType.LIFT, null, "LAW", null, RuleCondition.CODE_MATCH, null, null, codes,
                Duration.ZERO, AlertPriority.CRITICAL, "initial", "engineer", NOW, SourceChannel.WEB, "corr-1");
    }

    private static RecordMetadata metadata() {
        return RecordMetadata.createdBy("engineer", NOW, SourceChannel.WEB, "corr-1");
    }
}
