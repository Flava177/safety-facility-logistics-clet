package gh.edu.clet.sfl.facilities.readiness.domain;

import gh.edu.clet.sfl.facilities.masterdata.domain.LocationReadinessStatus;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.util.List;
import java.util.Objects;

/**
 * The rules that turn blockers into a readiness status.
 *
 * <p>This is the heart of S152. Everything else in the readiness module records facts; this decides
 * what they mean, and it is deliberately a pure function of its inputs so the decision can be
 * exercised directly rather than only through a running service.
 *
 * <p>The rules, in order — the first that matches wins:
 * <ol>
 *   <li><strong>Any open CRITICAL blocker → {@link LocationReadinessStatus#BLOCKED}.</strong> This is
 *       the invariant SRS-SFL-S152-01 names and the one {@link #requireReadyPermitted} enforces
 *       against any attempt to set READY by hand.</li>
 *   <li>Any open MAJOR or MINOR blocker → {@link LocationReadinessStatus#DEGRADED}. A real defect that
 *       does not stop use.</li>
 *   <li>Never assessed → {@link LocationReadinessStatus#UNKNOWN}. Distinct from READY, and the
 *       distinction matters: an unassessed examination hall is not a passed one.</li>
 *   <li>Otherwise → {@link LocationReadinessStatus#READY}. Advisory blockers do not change status;
 *       they are noted and reported.</li>
 * </ol>
 *
 * <p>The score is the weighted percentage of checklist items passed, reported alongside the status
 * rather than driving it. A room can score 95% and still be BLOCKED because the one thing that failed
 * was the fire door — which is exactly why severity, not score, decides.
 */
public final class ReadinessPolicy {

    private ReadinessPolicy() {
    }

    /**
     * Computes readiness from the currently open blockers.
     *
     * @param openBlockers every unresolved blocker for the space
     * @param score the weighted checklist score, 0–100
     * @param everAssessed whether the space has any assessment at all
     */
    public static ReadinessOutcome evaluate(List<ReadinessBlocker> openBlockers, int score, boolean everAssessed) {
        List<ReadinessBlocker> open = openBlockers == null
                ? List.of()
                : openBlockers.stream().filter(ReadinessBlocker::isOpen).toList();

        int critical = count(open, BlockerSeverity.CRITICAL);
        int major = count(open, BlockerSeverity.MAJOR);
        int minor = count(open, BlockerSeverity.MINOR);
        int advisory = count(open, BlockerSeverity.ADVISORY);

        LocationReadinessStatus status;
        if (critical > 0) {
            status = LocationReadinessStatus.BLOCKED;
        } else if (major > 0 || minor > 0) {
            status = LocationReadinessStatus.DEGRADED;
        } else if (!everAssessed) {
            status = LocationReadinessStatus.UNKNOWN;
        } else {
            status = LocationReadinessStatus.READY;
        }

        return new ReadinessOutcome(status, clampScore(score), open, critical, major, minor, advisory);
    }

    /**
     * The weighted percentage of checklist items passed.
     *
     * <p>A checklist whose items all carry zero weight scores 100 when nothing failed and 0 otherwise —
     * a pure pass/fail list is a legitimate configuration, and dividing by its zero total weight is not.
     */
    public static int score(List<ReadinessAssessmentItem> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        int totalWeight = items.stream().mapToInt(ReadinessAssessmentItem::weight).sum();
        if (totalWeight == 0) {
            return items.stream().allMatch(ReadinessAssessmentItem::passed) ? 100 : 0;
        }
        int earned = items.stream().mapToInt(ReadinessAssessmentItem::earnedWeight).sum();
        return clampScore(Math.round((earned * 100f) / totalWeight));
    }

    /**
     * Refuses an attempt to mark a space READY while critical blockers are open.
     *
     * <p>Called from the one place a status can be set by hand. Without it the derived status would be
     * correct and the manual override would not, which is the failure the requirement names.
     */
    public static void requireReadyPermitted(LocationReadinessStatus requested,
            List<ReadinessBlocker> openBlockers) {
        Objects.requireNonNull(requested, "requested status is required");
        if (requested != LocationReadinessStatus.READY) {
            return;
        }
        long critical = openBlockers == null ? 0L : openBlockers.stream()
                .filter(ReadinessBlocker::blocksReadiness)
                .count();
        if (critical > 0) {
            throw new FacilitiesException.ReadinessBlockedException((int) critical);
        }
    }

    private static int count(List<ReadinessBlocker> open, BlockerSeverity severity) {
        return (int) open.stream().filter(blocker -> blocker.severity() == severity).count();
    }

    private static int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }
}
