package gh.edu.clet.sfl.facilities.spaceplanning.domain.policy;

import gh.edu.clet.sfl.facilities.spaceplanning.domain.UtilisationSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

/**
 * The utilisation metric and the planning-signal rules - SRS-SFL-S158-03.
 *
 * <h2>The SRS does not define the metric, so this does</h2>
 *
 * The acceptance criterion reads "booked at under 20% of capacity over a full reporting period". That
 * sentence mixes two things - how often a space is used, and how full it is when it is - and the SRS
 * defines neither. This class uses the standard higher-education space-utilisation measure, which
 * multiplies the two:
 *
 * <pre>
 *   frequency rate   = used minutes / available minutes             (capped at 1)
 *   occupancy rate   = mean expected attendees per taken-up booking / capacity
 *   utilisation rate = frequency rate x occupancy rate               (capped at 1)
 * </pre>
 *
 * <ul>
 *   <li><strong>Used minutes</strong> are S159's {@code usedMinutes}: bookings actually taken up
 *       ({@code IN_USE} or {@code COMPLETED}), clipped to the period. A no-show is a booking and not a
 *       use; it counts as zero, and the count is kept on the snapshot so the reason is visible.</li>
 *   <li><strong>Available minutes</strong> are the period's configured bookable hours: each day whose
 *       day-of-week is in {@code space-planning.utilisation.available-days} contributes
 *       {@code space-planning.utilisation.available-hours-per-day} hours. Using the whole 24x7 period
 *       would make every room look empty.</li>
 *   <li><strong>Attendees</strong> are the booker's {@code expectedAttendees}. S159 records no head
 *       count and SFL has no occupancy sensor for most rooms, so this is an estimate by the person who
 *       booked. Flagged in the gap report.</li>
 * </ul>
 *
 * <p>So a 100-seat hall used for 10 of 50 available hours by groups of 50 scores {@code 0.2 x 0.5 = 10%}.
 * A room with no capacity recorded in S152 has no occupancy rate and is not evaluated; a room with no
 * bookings scores zero.
 *
 * <h2>Reporting periods</h2>
 *
 * Fixed-length windows of {@code space-planning.utilisation.period-days} days, aligned to Monday
 * 1 January 2024 00:00 UTC (CLET's sites are on GMT), so the default of seven is the ISO week. Only a
 * <em>complete</em> period is ever evaluated - "over a full reporting period" - and changing the length
 * realigns the windows from the next run.
 */
public final class UtilisationPolicy {

    /** A Monday. Periods are counted from here so the default of seven days is the ISO week. */
    static final Instant ANCHOR = Instant.parse("2024-01-01T00:00:00Z");
    private static final int SCALE = 4;

    private UtilisationPolicy() {
    }

    public record Period(Instant start, Instant end) {
    }

    public record Rates(BigDecimal frequencyRate, BigDecimal occupancyRate, BigDecimal utilisationRate) {
    }

    /** The most recent period that has fully ended by {@code now}. */
    public static Period latestCompletePeriod(Instant now, int periodDays) {
        long days = Math.max(1, periodDays);
        long elapsed = Duration.between(ANCHOR, now).toDays();
        long completed = Math.floorDiv(elapsed, days);
        Instant end = ANCHOR.plus(Duration.ofDays(completed * days));
        return new Period(end.minus(Duration.ofDays(days)), end);
    }

    /** The period ending at {@code end}, which the caller asserts is on a period boundary. */
    public static Period periodEndingAt(Instant end, int periodDays) {
        return new Period(end.minus(Duration.ofDays(Math.max(1, periodDays))), end);
    }

    public static boolean isPeriodBoundary(Instant instant, int periodDays) {
        long seconds = Duration.between(ANCHOR, instant).getSeconds();
        return seconds >= 0 && seconds % Duration.ofDays(Math.max(1, periodDays)).getSeconds() == 0;
    }

    public static long availableMinutes(Period period, Set<DayOfWeek> days, int hoursPerDay) {
        long minutes = 0;
        LocalDate day = LocalDate.ofInstant(period.start(), ZoneOffset.UTC);
        LocalDate last = LocalDate.ofInstant(period.end(), ZoneOffset.UTC);
        while (day.isBefore(last)) {
            if (days.contains(day.getDayOfWeek())) {
                minutes += (long) Math.max(0, Math.min(24, hoursPerDay)) * 60;
            }
            day = day.plusDays(1);
        }
        return minutes;
    }

    public static Rates rates(Integer capacity, int takenUpCount, long usedMinutes, long totalExpectedAttendees,
            long availableMinutes) {
        if (availableMinutes <= 0) {
            return new Rates(null, null, null);
        }
        BigDecimal frequency = cap(BigDecimal.valueOf(usedMinutes)
                .divide(BigDecimal.valueOf(availableMinutes), SCALE, RoundingMode.HALF_UP));
        if (capacity == null || capacity <= 0) {
            return new Rates(frequency, null, null);
        }
        BigDecimal occupancy = takenUpCount == 0 ? BigDecimal.ZERO.setScale(SCALE)
                : BigDecimal.valueOf(totalExpectedAttendees)
                        .divide(BigDecimal.valueOf((long) takenUpCount * capacity), SCALE, RoundingMode.HALF_UP);
        BigDecimal utilisation = cap(frequency.multiply(occupancy).setScale(SCALE, RoundingMode.HALF_UP));
        return new Rates(frequency, occupancy, utilisation);
    }

    /** The share of capacity the S152 register plans to fill, capped at 1; {@code null} without capacity. */
    public static BigDecimal plannedOccupancy(int plannedHeadcount, Integer capacity) {
        if (capacity == null || capacity <= 0) {
            return null;
        }
        return cap(BigDecimal.valueOf(plannedHeadcount).divide(BigDecimal.valueOf(capacity), SCALE,
                RoundingMode.HALF_UP));
    }

    /** "Booked at under 20% of capacity over a full reporting period." Strictly under. */
    public static boolean underUtilised(UtilisationSnapshot snapshot, BigDecimal threshold) {
        return snapshot.observable() && snapshot.utilisationRate().compareTo(threshold) < 0;
    }

    /** This one period shows the plan exceeding observed use by at least {@code gapThreshold}. */
    public static boolean gapInPeriod(UtilisationSnapshot snapshot, BigDecimal gapThreshold) {
        return snapshot.observable() && snapshot.plannedHeadcount() > 0 && snapshot.plannedOccupancyRate() != null
                && snapshot.plannedOccupancyRate().subtract(snapshot.utilisationRate()).compareTo(gapThreshold) >= 0;
    }

    /**
     * "Persistent": the gap holds in each of the newest {@code periods} snapshots, and they are
     * consecutive. A missing week breaks persistence rather than being skipped over - two gaps a month
     * apart are two observations, not a trend.
     *
     * @param newestFirst the room's snapshots, most recent first
     */
    public static boolean persistentGap(List<UtilisationSnapshot> newestFirst, int periods, BigDecimal gapThreshold) {
        int required = Math.max(1, periods);
        if (newestFirst.size() < required) {
            return false;
        }
        for (int i = 0; i < required; i++) {
            UtilisationSnapshot snapshot = newestFirst.get(i);
            if (!gapInPeriod(snapshot, gapThreshold)) {
                return false;
            }
            if (i > 0 && !newestFirst.get(i - 1).periodStart().equals(snapshot.periodEnd())) {
                return false;
            }
        }
        return true;
    }

    public static BigDecimal percent(int value) {
        return BigDecimal.valueOf(Math.max(0, value)).divide(BigDecimal.valueOf(100), SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal cap(BigDecimal value) {
        return value.compareTo(BigDecimal.ONE) > 0 ? BigDecimal.ONE.setScale(SCALE) : value;
    }
}
