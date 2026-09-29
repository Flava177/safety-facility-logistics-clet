package gh.edu.clet.sfl.facilities.construction.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.facilities.construction.domain.VariationOrder;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** SRS-SFL-S176-03's escalation rule, pure and off any clock or repository. */
class VariationEscalationPolicyTest {

    private static final BigDecimal BASELINE = new BigDecimal("500000.00");
    private static final BigDecimal THRESHOLD = BigDecimal.TEN;
    private static final Instant NOW = Instant.parse("2026-09-28T08:00:00Z");
    private final UUID projectId = UUID.randomUUID();

    private VariationOrder submitted(String delta) {
        return VariationOrder.submit(UUID.randomUUID(), "VO-MAIN-00001", project(), "Change", new BigDecimal(delta),
                "GHS", "Reason", "pm", NOW, SourceChannel.WEB, "corr");
    }

    private gh.edu.clet.sfl.facilities.construction.domain.ConstructionProject project() {
        return gh.edu.clet.sfl.facilities.construction.domain.ConstructionProject.register(projectId, "CP-MAIN-00001",
                "MAIN", "Title",
                new gh.edu.clet.sfl.facilities.construction.domain.ConstructionProject.Definition("Scope", BASELINE,
                        "GHS", "FUND-1", null, List.of("FIT_OUT")),
                "pm", "pm", NOW, SourceChannel.WEB, "corr");
    }

    @Test
    void a_variation_below_the_threshold_needs_no_escalation() {
        VariationOrder candidate = submitted("30000.00");

        VariationEscalationPolicy.Assessment assessment = VariationEscalationPolicy.assess(candidate, BASELINE,
                List.of(candidate), THRESHOLD);

        assertThat(assessment.escalationRequired()).isFalse();
    }

    @Test
    void a_variation_crossing_the_threshold_needs_escalation() {
        VariationOrder candidate = submitted("60000.00");

        VariationEscalationPolicy.Assessment assessment = VariationEscalationPolicy.assess(candidate, BASELINE,
                List.of(candidate), THRESHOLD);

        assertThat(assessment.escalationRequired()).isTrue();
        assertThat(assessment.reason()).contains("escalated approval required");
    }

    @Test
    void a_cost_saving_never_needs_escalation_even_past_the_threshold() {
        VariationOrder saving = submitted("-90000.00");

        VariationEscalationPolicy.Assessment assessment = VariationEscalationPolicy.assess(saving, BASELINE,
                List.of(saving), THRESHOLD);

        assertThat(assessment.escalationRequired()).isFalse();
    }

    @Test
    void a_variation_is_blocked_while_another_awaits_escalation_even_if_it_alone_would_not_cross() {
        VariationOrder held = submitted("60000.00").holdForEscalation("Over threshold", BigDecimal.valueOf(12), "pm",
                NOW, SourceChannel.WEB, "corr");
        VariationOrder small = submitted("500.00");

        VariationEscalationPolicy.Assessment assessment = VariationEscalationPolicy.assess(small, BASELINE,
                List.of(held, small), THRESHOLD);

        assertThat(assessment.escalationRequired()).isTrue();
        assertThat(assessment.blocked()).isTrue();
        assertThat(assessment.reason()).contains("held until it is obtained");
    }

    @Test
    void net_of_approved_variations_is_what_crosses_the_threshold() {
        VariationOrder approvedSaving = submitted("-40000.00").approve("director", null, BigDecimal.ZERO, NOW,
                SourceChannel.WEB, "corr");
        VariationOrder candidate = submitted("80000.00");

        // Net after this one: -40000 + 80000 = 40000, which is 8% - under the 10% threshold.
        VariationEscalationPolicy.Assessment assessment = VariationEscalationPolicy.assess(candidate, BASELINE,
                List.of(approvedSaving, candidate), THRESHOLD);

        assertThat(assessment.escalationRequired()).isFalse();
    }
}
