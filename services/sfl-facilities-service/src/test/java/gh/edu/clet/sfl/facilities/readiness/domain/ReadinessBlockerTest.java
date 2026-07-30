package gh.edu.clet.sfl.facilities.readiness.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Blocker lifecycle (SRS-SFL-S152-05). */
class ReadinessBlockerTest {

    private static final Instant RAISED = Instant.parse("2026-07-30T09:00:00Z");
    private static final Instant RESOLVED = Instant.parse("2026-07-30T12:00:00Z");

    @Test
    void a_new_blocker_is_open() {
        ReadinessBlocker blocker = critical();

        assertThat(blocker.isOpen()).isTrue();
        assertThat(blocker.resolved()).isFalse();
        assertThat(blocker.blocksReadiness()).isTrue();
        assertThat(blocker.siteCode()).isEqualTo("MAIN");
    }

    @Test
    void resolving_requires_a_note() {
        assertThatThrownBy(() -> critical().resolve("  ", "technician", RESOLVED))
                .isInstanceOf(FacilitiesException.ValidationFailedException.class)
                .hasMessageContaining("resolution note is required");
        assertThatThrownBy(() -> critical().resolve(null, "technician", RESOLVED))
                .isInstanceOf(FacilitiesException.ValidationFailedException.class);
    }

    @Test
    void a_resolved_blocker_records_who_when_and_why() {
        ReadinessBlocker resolved = critical().resolve("Latch replaced and retested", "technician", RESOLVED);

        assertThat(resolved.resolved()).isTrue();
        assertThat(resolved.resolvedBy()).isEqualTo("technician");
        assertThat(resolved.resolvedAt()).isEqualTo(RESOLVED);
        assertThat(resolved.resolutionNotes()).isEqualTo("Latch replaced and retested");
        assertThat(resolved.blocksReadiness()).isFalse();
    }

    @Test
    void a_blocker_cannot_be_resolved_twice() {
        ReadinessBlocker resolved = critical().resolve("Fixed", "technician", RESOLVED);

        assertThatThrownBy(() -> resolved.resolve("Fixed again", "technician", RESOLVED))
                .isInstanceOf(FacilitiesException.InvalidStateTransitionException.class)
                .hasMessageContaining("already resolved");
    }

    @Test
    void age_counts_to_now_while_open_and_stops_at_resolution() {
        ReadinessBlocker open = critical();
        ReadinessBlocker closed = open.resolve("Fixed", "technician", RESOLVED);

        assertThat(open.ageAt(RESOLVED)).isEqualTo(Duration.ofHours(3));
        assertThat(closed.ageAt(RESOLVED.plus(Duration.ofDays(5)))).isEqualTo(Duration.ofHours(3));
    }

    private static ReadinessBlocker critical() {
        return ReadinessBlocker.raise(UUID.randomUUID(), "main", null, BlockerSource.CHECKLIST_ITEM,
                "FIRE-EGRESS", BlockerSeverity.CRITICAL, "Fire door will not latch", "assessor", RAISED);
    }
}
