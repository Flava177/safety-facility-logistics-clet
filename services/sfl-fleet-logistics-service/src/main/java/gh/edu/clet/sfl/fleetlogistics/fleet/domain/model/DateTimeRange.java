package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A half-open instant range {@code [start, end)} used for assignment periods.
 *
 * <p>Half-open is deliberate: a trip ending at 12:00 and another starting at 12:00 do <em>not</em>
 * overlap, which is what fleet officers expect when they schedule back-to-back movements. The same
 * semantics are used by the database exclusion constraints on {@code trips}.
 */
public record DateTimeRange(Instant start, Instant end) {

    public DateTimeRange {
        Objects.requireNonNull(start, "start is required");
        Objects.requireNonNull(end, "end is required");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end must be after start");
        }
    }

    public static DateTimeRange of(Instant start, Instant end) {
        return new DateTimeRange(start, end);
    }

    public boolean overlaps(DateTimeRange other) {
        Objects.requireNonNull(other, "other is required");
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    public boolean contains(Instant instant) {
        Objects.requireNonNull(instant, "instant is required");
        return !instant.isBefore(start) && instant.isBefore(end);
    }

    public Duration duration() {
        return Duration.between(start, end);
    }
}
