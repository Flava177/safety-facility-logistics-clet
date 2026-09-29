package gh.edu.clet.sfl.facilities.cleaning.domain.policy;

import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningTask;
import gh.edu.clet.sfl.facilities.cleaning.domain.SlaBreachType;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskOrigin;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskStatus;
import gh.edu.clet.sfl.facilities.cleaning.domain.VendorSlaTerms;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Vendor SLA compliance, computed - SRS-SFL-S169-03.
 *
 * <h2>What this reads, and what it refuses to read</h2>
 *
 * Only the task's own lifecycle timestamps ({@code requestedAt}, {@code windowStart},
 * {@code startedAt}, {@code completedAt}) - every one set by the service clock on a transition - and
 * the occupant's rating. {@link CleaningTask#vendorReportedCompletedAt()} is never an input to a
 * breach; the SRS validation rule is that a breach "cannot be overridden by a vendor-supplied completion
 * time that conflicts with" the task's timestamps, and the simplest way to hold that is for the policy
 * not to be able to see it. It appears here only in {@link #discrepancy}, which records the conflict.
 *
 * <h2>The three clocks</h2>
 * <ul>
 *   <li><strong>Response</strong> - reactive requests only (the SRS acceptance criterion is about "a
 *       reactive request"): from the request being raised to the assignee starting. A request still not
 *       started after the contracted time is already in breach; the sweep records it without waiting.</li>
 *   <li><strong>Completion</strong> - every vendor task: from the request (reactive) or the window start
 *       (planned) to completion.</li>
 *   <li><strong>Quality</strong> - an occupant rating below the contracted floor.</li>
 * </ul>
 */
public final class SlaCompliancePolicy {

    private SlaCompliancePolicy() {
    }

    /** A breach the caller should record, with the measurement it rests on. */
    public record Finding(SlaBreachType type, BigDecimal contracted, BigDecimal actual, String basis) {
    }

    /** Time-based breaches for a vendor task as it stands at {@code now}. */
    public static List<Finding> timeFindings(CleaningTask task, VendorSlaTerms terms, Instant now) {
        List<Finding> findings = new ArrayList<>();
        response(task, terms, now).ifPresent(findings::add);
        completion(task, terms).ifPresent(findings::add);
        return List.copyOf(findings);
    }

    static Optional<Finding> response(CleaningTask task, VendorSlaTerms terms, Instant now) {
        if (task.origin() != TaskOrigin.REACTIVE) {
            return Optional.empty();
        }
        Instant stoppedAt = task.startedAt();
        if (stoppedAt == null) {
            if (task.status() == TaskStatus.CANCELLED || task.status() == TaskStatus.COMPLETED) {
                return Optional.empty();
            }
            stoppedAt = now;
        }
        Duration taken = Duration.between(task.requestedAt(), stoppedAt);
        if (taken.compareTo(terms.responseTime()) <= 0) {
            return Optional.empty();
        }
        String basis = task.startedAt() == null
                ? "not attended " + minutes(taken) + " min after the request was raised; contracted "
                        + terms.responseMinutes() + " min"
                : "attended " + minutes(taken) + " min after the request was raised; contracted "
                        + terms.responseMinutes() + " min";
        return Optional.of(new Finding(SlaBreachType.RESPONSE, BigDecimal.valueOf(terms.responseMinutes()),
                minutes(taken), basis));
    }

    static Optional<Finding> completion(CleaningTask task, VendorSlaTerms terms) {
        if (task.completedAt() == null) {
            return Optional.empty();
        }
        Instant clockStart = task.origin() == TaskOrigin.REACTIVE ? task.requestedAt() : task.windowStart();
        Duration taken = Duration.between(clockStart, task.completedAt());
        if (taken.compareTo(terms.completionTime()) <= 0) {
            return Optional.empty();
        }
        return Optional.of(new Finding(SlaBreachType.COMPLETION, BigDecimal.valueOf(terms.completionMinutes()),
                minutes(taken), "completed " + minutes(taken) + " min after "
                        + (task.origin() == TaskOrigin.REACTIVE ? "the request was raised" : "the window opened")
                        + " (recorded completion, not vendor-reported); contracted " + terms.completionMinutes()
                        + " min"));
    }

    /** A rating below the floor. A rating equal to the floor meets it. */
    public static Optional<Finding> quality(int rating, VendorSlaTerms terms) {
        BigDecimal actual = BigDecimal.valueOf(rating);
        if (actual.compareTo(terms.qualityFloor()) >= 0) {
            return Optional.empty();
        }
        return Optional.of(new Finding(SlaBreachType.QUALITY, terms.qualityFloor(), actual,
                "occupant rating " + rating + " below the contracted floor " + terms.qualityFloor().toPlainString()));
    }

    /**
     * The vendor's claimed completion against the recorded one.
     *
     * @return {@code reported - recorded} in seconds when further apart than {@code tolerance}; empty when
     *         the vendor reported nothing or agreed. Negative means the vendor claims an earlier finish.
     */
    public static Optional<Long> discrepancy(Instant recorded, Instant reported, Duration tolerance) {
        if (recorded == null || reported == null) {
            return Optional.empty();
        }
        Duration gap = Duration.between(recorded, reported);
        return gap.abs().compareTo(tolerance) > 0 ? Optional.of(gap.getSeconds()) : Optional.empty();
    }

    private static BigDecimal minutes(Duration duration) {
        return BigDecimal.valueOf(duration.getSeconds()).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
}
