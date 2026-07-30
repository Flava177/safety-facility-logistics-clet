package gh.edu.clet.sfl.facilities.readiness.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.facilities.masterdata.domain.LocationReadinessStatus;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The readiness rules (SRS-SFL-S152-01, -05).
 *
 * <p>The most important test file in this build. Everything else records facts; this decides what they
 * mean, and the first test below is the invariant the whole system exists to enforce.
 */
class ReadinessPolicyTest {

    private static final UUID ROOM = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-07-30T09:00:00Z");

    @Test
    void an_open_critical_blocker_blocks_the_space() {
        ReadinessOutcome outcome = ReadinessPolicy.evaluate(
                List.of(blocker(BlockerSeverity.CRITICAL)), 95, true);

        assertThat(outcome.status()).isEqualTo(LocationReadinessStatus.BLOCKED);
        assertThat(outcome.criticalCount()).isEqualTo(1);
        assertThat(outcome.isReady()).isFalse();
    }

    @Test
    void a_high_score_does_not_override_a_critical_blocker() {
        // A room can score 95% and still be unusable because the one thing that failed was the fire
        // door. Severity decides; the score reports.
        ReadinessOutcome outcome = ReadinessPolicy.evaluate(
                List.of(blocker(BlockerSeverity.CRITICAL)), 100, true);

        assertThat(outcome.status()).isEqualTo(LocationReadinessStatus.BLOCKED);
        assertThat(outcome.score()).isEqualTo(100);
    }

    @Test
    void a_major_or_minor_blocker_degrades_the_space() {
        assertThat(ReadinessPolicy.evaluate(List.of(blocker(BlockerSeverity.MAJOR)), 80, true).status())
                .isEqualTo(LocationReadinessStatus.DEGRADED);
        assertThat(ReadinessPolicy.evaluate(List.of(blocker(BlockerSeverity.MINOR)), 80, true).status())
                .isEqualTo(LocationReadinessStatus.DEGRADED);
    }

    @Test
    void an_advisory_blocker_leaves_the_space_ready() {
        ReadinessOutcome outcome = ReadinessPolicy.evaluate(
                List.of(blocker(BlockerSeverity.ADVISORY)), 90, true);

        assertThat(outcome.status()).isEqualTo(LocationReadinessStatus.READY);
        assertThat(outcome.advisoryCount()).isEqualTo(1);
    }

    @Test
    void a_never_assessed_space_is_unknown_rather_than_ready() {
        ReadinessOutcome outcome = ReadinessPolicy.evaluate(List.of(), 0, false);

        assertThat(outcome.status()).isEqualTo(LocationReadinessStatus.UNKNOWN);
    }

    @Test
    void an_assessed_space_with_no_open_blockers_is_ready() {
        ReadinessOutcome outcome = ReadinessPolicy.evaluate(List.of(), 100, true);

        assertThat(outcome.status()).isEqualTo(LocationReadinessStatus.READY);
        assertThat(outcome.summary()).isEqualTo("All readiness checks passed.");
    }

    @Test
    void resolved_blockers_are_ignored() {
        ReadinessBlocker resolved = blocker(BlockerSeverity.CRITICAL)
                .resolve("Door adjusted and retested", "technician", NOW);

        ReadinessOutcome outcome = ReadinessPolicy.evaluate(List.of(resolved), 100, true);

        assertThat(outcome.status()).isEqualTo(LocationReadinessStatus.READY);
        assertThat(outcome.openBlockers()).isEmpty();
    }

    @Test
    void refuses_a_manual_ready_while_a_critical_blocker_is_open() {
        assertThatThrownBy(() -> ReadinessPolicy.requireReadyPermitted(LocationReadinessStatus.READY,
                List.of(blocker(BlockerSeverity.CRITICAL), blocker(BlockerSeverity.CRITICAL))))
                .isInstanceOf(FacilitiesException.ReadinessBlockedException.class)
                .hasMessageContaining("2 critical blocker(s) remain open");
    }

    @Test
    void permits_a_manual_blocked_or_degraded_whatever_is_open() {
        ReadinessPolicy.requireReadyPermitted(LocationReadinessStatus.BLOCKED,
                List.of(blocker(BlockerSeverity.CRITICAL)));
        ReadinessPolicy.requireReadyPermitted(LocationReadinessStatus.DEGRADED,
                List.of(blocker(BlockerSeverity.CRITICAL)));
    }

    @Test
    void permits_a_manual_ready_once_the_critical_blockers_are_resolved() {
        ReadinessBlocker resolved = blocker(BlockerSeverity.CRITICAL).resolve("Fixed", "technician", NOW);

        ReadinessPolicy.requireReadyPermitted(LocationReadinessStatus.READY, List.of(resolved));
    }

    @Test
    void scores_by_weight_rather_than_by_count() {
        // Three items: one weight-3 pass, two weight-1 failures. By count that is 33%; by weight, 60%.
        List<ReadinessAssessmentItem> items = List.of(
                answered("FIRE", BlockerSeverity.CRITICAL, 3, true),
                answered("SIGNAGE", BlockerSeverity.MINOR, 1, false),
                answered("CLEAN", BlockerSeverity.MINOR, 1, false));

        assertThat(ReadinessPolicy.score(items)).isEqualTo(60);
    }

    @Test
    void a_pure_pass_fail_checklist_scores_100_or_0() {
        assertThat(ReadinessPolicy.score(List.of(answered("A", BlockerSeverity.MAJOR, 0, true))))
                .isEqualTo(100);
        assertThat(ReadinessPolicy.score(List.of(
                answered("A", BlockerSeverity.MAJOR, 0, true),
                answered("B", BlockerSeverity.MAJOR, 0, false))))
                .isZero();
    }

    @Test
    void an_empty_assessment_scores_zero() {
        assertThat(ReadinessPolicy.score(List.of())).isZero();
        assertThat(ReadinessPolicy.score(null)).isZero();
    }

    @Test
    void the_summary_counts_every_open_severity() {
        ReadinessOutcome outcome = ReadinessPolicy.evaluate(List.of(
                blocker(BlockerSeverity.CRITICAL),
                blocker(BlockerSeverity.MAJOR),
                blocker(BlockerSeverity.MAJOR),
                blocker(BlockerSeverity.ADVISORY)), 40, true);

        assertThat(outcome.summary())
                .isEqualTo("1 critical, 2 major, 0 minor and 1 advisory blocker(s) open.");
    }

    private static ReadinessBlocker blocker(BlockerSeverity severity) {
        return ReadinessBlocker.raise(ROOM, "MAIN", null, BlockerSource.CHECKLIST_ITEM, "ITEM",
                severity, severity + " defect", "assessor", NOW);
    }

    private static ReadinessAssessmentItem answered(String code, BlockerSeverity severity, int weight,
            boolean passed) {
        ReadinessChecklistItem item = ReadinessChecklistItem.of(UUID.randomUUID(), code, code + " check",
                severity, true, weight, 0);
        return ReadinessAssessmentItem.answered(UUID.randomUUID(), item, passed, null);
    }
}
