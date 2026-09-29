package gh.edu.clet.sfl.common.hse;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * Whether a Risk Assessment Library (S165) record may be relied on right now.
 *
 * <h2>Why this lives in the shared library</h2>
 *
 * <p>SRS-SFL-S173-03: "The linked risk assessment currency check reuses the same logic as S164-01, not
 * a separate implementation." S173 event logistics is in {@code sfl-facilities-service}; S164
 * permit-to-work and S165 itself will be in {@code sfl-safety-security-service}. Two deployables
 * cannot share a class any other way, so the rule sits here - and it is the only HSE rule that does.
 * Everything else about an assessment (authoring, versioning, review reminders) belongs to S165.
 *
 * <p>It is a pure function of a snapshot and a clock. No repository, no framework: whichever service
 * asks already holds the snapshot, from its own table (S165) or from a projection of S165's events
 * (S173, S176), and the answer must be the same in both.
 *
 * <h2>The rules, each from the SRS</h2>
 * <ol>
 *   <li>Nothing linked - S164-01 "No Risk Assessment Linked".</li>
 *   <li>Not published, or superseded by a later version - S165-01: a superseded version "is clearly
 *       marked as not current".</li>
 *   <li>Past its review date without a renewed sign-off - S164-01 "treated as not current";
 *       S165-02 "marks the assessment not-current once the due date passes".</li>
 *   <li>A higher-risk assessment whose only sign-off is its own author - S165-02: "a named competent
 *       reviewer, not the original author acting alone, for higher-risk assessments".</li>
 * </ol>
 *
 * <p>The review date is exclusive: an assessment due on the 1st at 00:00 is lapsed at 00:00 on the
 * 1st, not a day later. A boundary that let an expired assessment through for one more day is the kind
 * nobody notices until the day it matters.
 */
public final class RiskAssessmentCurrency {

    private RiskAssessmentCurrency() {
    }

    /** The answer for one assessment at one instant. */
    public static Verdict assess(Snapshot snapshot, Instant at) {
        Objects.requireNonNull(at, "at is required");
        if (snapshot == null) {
            return Verdict.notCurrent(Reason.NONE_LINKED);
        }
        if (snapshot.status() == Status.SUPERSEDED) {
            return Verdict.notCurrent(Reason.SUPERSEDED);
        }
        if (snapshot.status() != Status.PUBLISHED) {
            return Verdict.notCurrent(Reason.NOT_PUBLISHED);
        }
        if (snapshot.reviewDueAt() == null || !snapshot.reviewDueAt().isAfter(at)) {
            return Verdict.notCurrent(Reason.REVIEW_LAPSED);
        }
        if (snapshot.riskLevel().requiresIndependentReviewer() && !independentlySignedOff(snapshot)) {
            return Verdict.notCurrent(Reason.NOT_INDEPENDENTLY_SIGNED_OFF);
        }
        return Verdict.isCurrent();
    }

    private static boolean independentlySignedOff(Snapshot snapshot) {
        String reviewer = snapshot.signedOffBy();
        return reviewer != null && !reviewer.isBlank()
                && !reviewer.strip().toLowerCase(Locale.ROOT).equals(
                        snapshot.authorId() == null ? "" : snapshot.authorId().strip().toLowerCase(Locale.ROOT));
    }

    /** S165's lifecycle, as far as currency needs it. */
    public enum Status {
        DRAFT,
        PUBLISHED,
        SUPERSEDED,
        WITHDRAWN
    }

    /** S165-02: "a review interval driven by its risk level (shorter for higher risk)". */
    public enum RiskLevel {
        LOW(false),
        MEDIUM(false),
        HIGH(true),
        CRITICAL(true);

        private final boolean independentReviewer;

        RiskLevel(boolean independentReviewer) {
            this.independentReviewer = independentReviewer;
        }

        public boolean requiresIndependentReviewer() {
            return independentReviewer;
        }
    }

    public enum Reason {
        /** S164-01 "No Risk Assessment Linked - required for this work type; refused." */
        NONE_LINKED,
        /** A later version exists. The link should follow it, not the old one. */
        SUPERSEDED,
        NOT_PUBLISHED,
        /** S164-01 "Risk Assessment Not Current - linked assessment past its review date". */
        REVIEW_LAPSED,
        NOT_INDEPENDENTLY_SIGNED_OFF
    }

    /**
     * What a consumer knows about one assessment version.
     *
     * @param signedOffBy the reviewer on the latest sign-off, which for a higher-risk assessment must
     *        not be the author
     */
    public record Snapshot(
            String assessmentId,
            int version,
            Status status,
            RiskLevel riskLevel,
            Instant reviewDueAt,
            String authorId,
            String signedOffBy) {

        public Snapshot {
            if (assessmentId == null || assessmentId.isBlank()) {
                throw new IllegalArgumentException("assessmentId is required");
            }
            Objects.requireNonNull(status, "status is required");
            Objects.requireNonNull(riskLevel, "riskLevel is required");
        }
    }

    /** Current, or not current with the reason a refusal message should name. */
    public record Verdict(boolean current, Reason reason) {

        static Verdict isCurrent() {
            return new Verdict(true, null);
        }

        static Verdict notCurrent(Reason reason) {
            return new Verdict(false, reason);
        }
    }
}
