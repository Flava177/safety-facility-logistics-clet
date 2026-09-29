package gh.edu.clet.sfl.facilities.cleaning.domain.policy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Whether cleaning capacity is free for a requested slot - SRS-SFL-S169-04.
 *
 * <p>Two rules, checked in this order because the first gives the more useful answer:
 *
 * <ol>
 *   <li><strong>The room.</strong> A room cannot have two overlapping cleaning commitments. If the hall
 *       is already being cleaned 09:00-10:00, a second crew there at 09:30 is not capacity, it is two
 *       crews in each other's way.</li>
 *   <li><strong>The site's crews.</strong> The configured number of crews at the site, against the
 *       <em>peak</em> number of commitments running at once inside the requested slot. Peak, not total:
 *       two cleans at 09:00-09:30 and 10:00-10:30 never need two crews, and counting them as if they did
 *       would refuse an event slot that is genuinely free.</li>
 * </ol>
 *
 * <p>Either way the refusal carries a named competing commitment (SRS-SFL-S169-04 validation) - the
 * earliest-starting one that is actually in the way - never a bare "unavailable".
 */
public final class CleaningCapacityPolicy {

    private CleaningCapacityPolicy() {
    }

    /** An existing commitment: a live task's window. {@code description} is shown to S173 verbatim. */
    public record Commitment(UUID taskId, UUID roomId, Instant from, Instant to, String description) {
    }

    /** {@code competing} is null exactly when {@code available}. */
    public record Result(boolean available, Commitment competing, String reason, int peakConcurrent) {

        static Result free(int peak) {
            return new Result(true, null, null, peak);
        }
    }

    public static Result evaluate(UUID roomId, Instant from, Instant to, int crews, List<Commitment> commitments) {
        Objects.requireNonNull(from, "from is required");
        Objects.requireNonNull(to, "to is required");
        List<Commitment> overlapping = commitments.stream()
                .filter(commitment -> commitment.from().isBefore(to) && from.isBefore(commitment.to()))
                .sorted(Comparator.comparing(Commitment::from).thenComparing(Commitment::description))
                .toList();

        if (roomId != null) {
            for (Commitment commitment : overlapping) {
                if (roomId.equals(commitment.roomId())) {
                    return new Result(false, commitment, "The room already has a cleaning commitment in that slot.",
                            peak(overlapping, from, to).count());
                }
            }
        }

        Peak peak = peak(overlapping, from, to);
        int available = Math.max(1, crews);
        if (peak.count() >= available) {
            return new Result(false, peak.first(), "All " + available + " cleaning crew(s) at the site are committed "
                    + "at that time.", peak.count());
        }
        return Result.free(peak.count());
    }

    private record Peak(int count, Commitment first) {
    }

    /**
     * The largest number of commitments running at one instant inside {@code [from, to)}.
     *
     * <p>Concurrency can only rise at a commitment's start, so the candidates are the slot start and
     * every start inside the slot. Half-open windows: one ending at 10:00 and one starting at 10:00 are
     * never counted together.
     */
    private static Peak peak(List<Commitment> overlapping, Instant from, Instant to) {
        List<Instant> candidates = new ArrayList<>();
        candidates.add(from);
        overlapping.stream().map(Commitment::from).filter(start -> start.isAfter(from) && start.isBefore(to))
                .forEach(candidates::add);
        int best = 0;
        Commitment bestFirst = null;
        for (Instant instant : candidates) {
            List<Commitment> running = overlapping.stream()
                    .filter(commitment -> !commitment.from().isAfter(instant) && instant.isBefore(commitment.to()))
                    .toList();
            if (running.size() > best) {
                best = running.size();
                bestFirst = running.get(0);
            }
        }
        return new Peak(best, bestFirst);
    }
}
