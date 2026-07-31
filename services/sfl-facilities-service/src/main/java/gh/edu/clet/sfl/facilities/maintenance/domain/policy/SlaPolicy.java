package gh.edu.clet.sfl.facilities.maintenance.domain.policy;

import gh.edu.clet.sfl.facilities.maintenance.domain.FaultPriority;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * How long maintenance work has, and when it escalates.
 *
 * <p>SRS-SFL-S153-02: "The system shall calculate SLA timers from configurable priority, severity,
 * site, operating mode and workflow type rules", and "Escalation rules must be evaluated using the
 * runtime configuration active at the time of evaluation."
 *
 * <h2>Why this is a value object and not a service</h2>
 *
 * The rules arrive as configuration, but applying them is arithmetic on a deadline — no repository,
 * no clock of its own, nothing to mock. Keeping it a value object means the SLA table for a site can
 * be built once per evaluation run and applied to a thousand work orders without a thousand
 * configuration reads, and means the interesting tests ("does a critical fault in examination mode
 * get four hours or two?") are plain assertions.
 *
 * <h2>The two things the SRS asks for that are easy to get wrong</h2>
 *
 * <ul>
 *   <li><strong>Operating mode compresses the SLA, it does not replace it.</strong> A site in
 *       {@link OperatingMode#EXAMINATION} multiplies every duration by
 *       {@link #examinationFactor} — normally less than one. Expressing it as a factor rather than a
 *       second table means a site that lengthens one priority's SLA cannot forget to lengthen its
 *       examination equivalent.</li>
 *   <li><strong>Escalation is a ladder, not a flag.</strong> Level 1 at the deadline, and one further
 *       level per {@link #escalationInterval} past it, capped at {@link #maxEscalationLevel}. A
 *       single boolean would notify once and then go quiet on an item nobody picked up, which is
 *       exactly the case escalation exists for.</li>
 * </ul>
 *
 * @param response how long to acknowledge and assign, by priority. Currently informational: it is
 *        carried so a later round can escalate un-assigned work separately from un-finished work.
 * @param resolution how long to finish, by priority. This is what a work order's due date is set from.
 * @param examinationFactor multiplier applied to both while the site is in examination mode.
 * @param escalationInterval how long between successive escalation levels once overdue.
 * @param maxEscalationLevel the ceiling. Beyond it, nothing further is raised — an item that has
 *        reached the top of the ladder is already with the person who can act on it.
 */
public record SlaPolicy(
        java.util.Map<FaultPriority, Duration> response,
        java.util.Map<FaultPriority, Duration> resolution,
        double examinationFactor,
        Duration escalationInterval,
        int maxEscalationLevel) {

    /**
     * The defaults, used when a site has configured nothing.
     *
     * <p>Chosen to be defensible rather than arbitrary: a critical fault is something that stops a
     * space being used, so four hours is a working half-day; low priority is a fortnight, which is
     * long enough to batch with other work and short enough that it is not a black hole.
     */
    public static SlaPolicy defaults() {
        return new SlaPolicy(
                java.util.Map.of(
                        FaultPriority.CRITICAL, Duration.ofMinutes(30),
                        FaultPriority.HIGH, Duration.ofHours(2),
                        FaultPriority.MEDIUM, Duration.ofHours(8),
                        FaultPriority.LOW, Duration.ofHours(24)),
                java.util.Map.of(
                        FaultPriority.CRITICAL, Duration.ofHours(4),
                        FaultPriority.HIGH, Duration.ofHours(24),
                        FaultPriority.MEDIUM, Duration.ofDays(3),
                        FaultPriority.LOW, Duration.ofDays(14)),
                0.5d,
                Duration.ofHours(4),
                3);
    }

    public SlaPolicy {
        Objects.requireNonNull(response, "response is required");
        Objects.requireNonNull(resolution, "resolution is required");
        Objects.requireNonNull(escalationInterval, "escalationInterval is required");
        if (examinationFactor <= 0d) {
            throw new IllegalArgumentException("examinationFactor must be greater than zero");
        }
        if (escalationInterval.isZero() || escalationInterval.isNegative()) {
            throw new IllegalArgumentException("escalationInterval must be positive");
        }
        if (maxEscalationLevel < 1) {
            throw new IllegalArgumentException("maxEscalationLevel must be at least one");
        }
    }

    /** When work of this priority, raised now at a site in this mode, must be finished. */
    public Instant resolutionDueFrom(Instant raisedAt, FaultPriority priority, OperatingMode mode) {
        return raisedAt.plus(adjust(resolution.getOrDefault(priority, defaultResolution(priority)), mode));
    }

    /** When work of this priority must have been acknowledged and assigned. */
    public Instant responseDueFrom(Instant raisedAt, FaultPriority priority, OperatingMode mode) {
        return raisedAt.plus(adjust(response.getOrDefault(priority, defaultResponse(priority)), mode));
    }

    /**
     * The escalation level an item is owed, given its deadline and the time now.
     *
     * <p>Zero while inside the SLA. One the moment it passes. One more per interval after that, up to
     * the cap. Pure arithmetic, which is what makes the evaluator idempotent: the same inputs give
     * the same level, so re-running it changes nothing on an item already at that level.
     */
    public int escalationLevelFor(Instant dueAt, Instant now) {
        if (dueAt == null || !now.isAfter(dueAt)) {
            return 0;
        }
        long overdueSeconds = Duration.between(dueAt, now).getSeconds();
        long intervals = overdueSeconds / escalationInterval.getSeconds();
        return (int) Math.min(maxEscalationLevel, 1L + intervals);
    }

    /** A vendor's contracted response time wins when it is tighter than the priority's own rule. */
    public Instant resolutionDueFrom(Instant raisedAt, FaultPriority priority, OperatingMode mode,
            Integer vendorResponseHours) {
        Instant standard = resolutionDueFrom(raisedAt, priority, mode);
        if (vendorResponseHours == null || vendorResponseHours <= 0) {
            return standard;
        }
        Instant contracted = raisedAt.plus(adjust(Duration.ofHours(vendorResponseHours), mode));
        return contracted.isBefore(standard) ? contracted : standard;
    }

    private Duration adjust(Duration base, OperatingMode mode) {
        if (mode != OperatingMode.EXAMINATION) {
            return base;
        }
        // Never rounds to zero: an SLA of "immediately" is a deadline nothing can meet, and every
        // item would be born escalated.
        long seconds = Math.max(60L, Math.round(base.getSeconds() * examinationFactor));
        return Duration.ofSeconds(seconds);
    }

    private static Duration defaultResolution(FaultPriority priority) {
        return defaults().resolution().get(priority);
    }

    private static Duration defaultResponse(FaultPriority priority) {
        return defaults().response().get(priority);
    }
}
