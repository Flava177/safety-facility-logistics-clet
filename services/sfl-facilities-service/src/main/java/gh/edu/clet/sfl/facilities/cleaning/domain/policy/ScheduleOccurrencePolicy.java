package gh.edu.clet.sfl.facilities.cleaning.domain.policy;

import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningFrequency;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningSchedule;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Where a routine schedule's occurrences fall - SRS-SFL-S169-01.
 *
 * <p>Pure arithmetic so it can be tested without a clock or a database, and so the generation sweep and
 * the capacity view cannot disagree about when a clean happens.
 *
 * <p>Times of day are local to the configured zone and converted to instants here, once. A rota says
 * "07:00", not "07:00 UTC"; Ghana has no daylight saving, but a zone rule belongs in one place in case
 * a future site is somewhere that does - {@link ZonedDateTime#of} resolves a gap or overlap by the
 * standard offset rules rather than silently dropping the occurrence.
 */
public final class ScheduleOccurrencePolicy {

    private ScheduleOccurrencePolicy() {
    }

    /** Occurrence starts in {@code [from, to)}, ascending. Empty for an inactive schedule. */
    public static List<Instant> occurrences(CleaningSchedule schedule, ZoneId zone, Instant from, Instant to) {
        Objects.requireNonNull(schedule, "schedule is required");
        Objects.requireNonNull(zone, "zone is required");
        if (!schedule.active() || !to.isAfter(from)) {
            return List.of();
        }
        List<Instant> found = new ArrayList<>();
        LocalDate day = from.atZone(zone).toLocalDate();
        LocalDate last = to.atZone(zone).toLocalDate();
        while (!day.isAfter(last)) {
            if (runsOn(schedule, day.getDayOfWeek())) {
                for (LocalTime time : schedule.timesOfDay()) {
                    Instant start = ZonedDateTime.of(day, time, zone).toInstant();
                    if (!start.isBefore(from) && start.isBefore(to)) {
                        found.add(start);
                    }
                }
            }
            day = day.plusDays(1);
        }
        found.sort(null);
        return List.copyOf(found);
    }

    static boolean runsOn(CleaningSchedule schedule, DayOfWeek day) {
        return schedule.frequency() == CleaningFrequency.DAILY || schedule.daysOfWeek().contains(day);
    }
}
