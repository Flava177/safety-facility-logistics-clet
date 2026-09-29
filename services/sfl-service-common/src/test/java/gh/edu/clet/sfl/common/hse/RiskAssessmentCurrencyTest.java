package gh.edu.clet.sfl.common.hse;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.common.hse.RiskAssessmentCurrency.Reason;
import gh.edu.clet.sfl.common.hse.RiskAssessmentCurrency.RiskLevel;
import gh.edu.clet.sfl.common.hse.RiskAssessmentCurrency.Snapshot;
import gh.edu.clet.sfl.common.hse.RiskAssessmentCurrency.Status;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The shared S164-01 / S165-02 / S173-03 currency rule, one case per SRS clause. */
class RiskAssessmentCurrencyTest {

    private static final Instant NOW = Instant.parse("2026-09-28T09:00:00Z");

    @Test
    @DisplayName("S164-01: nothing linked is not current, with its own reason")
    void nothing_linked() {
        assertThat(RiskAssessmentCurrency.assess(null, NOW).reason()).isEqualTo(Reason.NONE_LINKED);
    }

    @Test
    @DisplayName("a published, in-date, independently signed-off assessment is current")
    void current() {
        assertThat(RiskAssessmentCurrency.assess(snapshot(Status.PUBLISHED, RiskLevel.HIGH,
                NOW.plusSeconds(86_400), "author", "reviewer"), NOW).current()).isTrue();
    }

    @Test
    @DisplayName("S164-01 / S165-02: past its review date is not current")
    void review_lapsed() {
        assertThat(RiskAssessmentCurrency.assess(snapshot(Status.PUBLISHED, RiskLevel.LOW,
                NOW.minusSeconds(1), "author", "author"), NOW).reason()).isEqualTo(Reason.REVIEW_LAPSED);
    }

    @Test
    @DisplayName("the review date is exclusive - due now means lapsed now")
    void review_boundary_is_exclusive() {
        assertThat(RiskAssessmentCurrency.assess(snapshot(Status.PUBLISHED, RiskLevel.LOW, NOW, "a", "a"), NOW)
                .reason()).isEqualTo(Reason.REVIEW_LAPSED);
    }

    @Test
    @DisplayName("S165-01: a superseded version is not current")
    void superseded() {
        assertThat(RiskAssessmentCurrency.assess(snapshot(Status.SUPERSEDED, RiskLevel.LOW,
                NOW.plusSeconds(60), "a", "b"), NOW).reason()).isEqualTo(Reason.SUPERSEDED);
    }

    @Test
    @DisplayName("a draft is not current")
    void draft() {
        assertThat(RiskAssessmentCurrency.assess(snapshot(Status.DRAFT, RiskLevel.LOW,
                NOW.plusSeconds(60), "a", "b"), NOW).reason()).isEqualTo(Reason.NOT_PUBLISHED);
    }

    @Test
    @DisplayName("S165-02: a higher-risk assessment signed off by its own author alone is not current")
    void high_risk_author_only() {
        assertThat(RiskAssessmentCurrency.assess(snapshot(Status.PUBLISHED, RiskLevel.HIGH,
                NOW.plusSeconds(60), "Kofi", " kofi "), NOW).reason())
                .isEqualTo(Reason.NOT_INDEPENDENTLY_SIGNED_OFF);
    }

    @Test
    @DisplayName("a low-risk assessment may be signed off by its author")
    void low_risk_author_is_fine() {
        assertThat(RiskAssessmentCurrency.assess(snapshot(Status.PUBLISHED, RiskLevel.LOW,
                NOW.plusSeconds(60), "kofi", "kofi"), NOW).current()).isTrue();
    }

    private static Snapshot snapshot(Status status, RiskLevel level, Instant due, String author, String reviewer) {
        return new Snapshot("RA-1", 1, status, level, due, author, reviewer);
    }
}
