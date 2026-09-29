package gh.edu.clet.sfl.facilities.energy.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * A day or a calendar month, half-open {@code [start, end)}.
 *
 * <p>Days are UTC days. Ghana keeps GMT all year with no daylight saving, so for every CLET site the
 * UTC day is the local day; a future site in another zone would need a per-site zone here, which is
 * recorded in the gap report rather than half-built.
 */
public record EnergyPeriod(PeriodType type, LocalDate start) {

    public enum PeriodType {
        DAY,
        MONTH
    }

    public EnergyPeriod {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(start, "start");
        if (type == PeriodType.MONTH && start.getDayOfMonth() != 1) {
            throw new IllegalArgumentException("A month period starts on the first of the month");
        }
    }

    /** The period of {@code type} containing {@code day}. */
    public static EnergyPeriod containing(PeriodType type, LocalDate day) {
        return new EnergyPeriod(type, type == PeriodType.MONTH ? day.withDayOfMonth(1) : day);
    }

    public static EnergyPeriod month(LocalDate anyDay) {
        return containing(PeriodType.MONTH, anyDay);
    }

    /** Exclusive. */
    public LocalDate end() {
        return type == PeriodType.MONTH ? start.plusMonths(1) : start.plusDays(1);
    }

    public EnergyPeriod previous() {
        return new EnergyPeriod(type, type == PeriodType.MONTH ? start.minusMonths(1) : start.minusDays(1));
    }

    public Instant startInstant() {
        return start.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    public Instant endInstant() {
        return end().atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    public long minutes() {
        return Duration.between(startInstant(), endInstant()).toMinutes();
    }

    public boolean contains(LocalDate day) {
        return !day.isBefore(start) && day.isBefore(end());
    }

    /** {@code true} once the whole period lies in the past. */
    public boolean hasEndedBy(Instant now) {
        return !endInstant().isAfter(now);
    }

    public static LocalDate dayOf(Instant instant) {
        return LocalDate.ofInstant(instant, ZoneOffset.UTC);
    }
}
