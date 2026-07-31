package gh.edu.clet.sfl.facilities.booking.domain;

import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * When a booking runs, and how long the space is actually occupied for.
 *
 * <h2>Half-open intervals, and why the whole module turns on it</h2>
 *
 * A window is <strong>{@code [start, end)}</strong> — start inclusive, end exclusive. A booking that
 * ends at 10:00 and one that starts at 10:00 do <em>not</em> overlap.
 *
 * <p>This is the single most consequential decision in S159 and it is easy to get wrong in both
 * directions. Treat both ends as inclusive and every back-to-back booking in the estate reports a
 * phantom clash, so people stop trusting the conflict check. Treat both as exclusive and two bookings
 * sharing an instant slip past, so the hall is double-booked on the hour — which is precisely when
 * lectures change over.
 *
 * <p>The same rule is applied in three places and they must agree: {@link #overlaps} here, the
 * application-level check that produces a readable error, and the PostgreSQL exclusion constraint
 * that holds under concurrency. Postgres {@code tstzrange} is half-open by default, so the database
 * and this class already speak the same language — which is why the constraint is written
 * {@code '[)'} explicitly rather than left to a default somebody might change.
 *
 * <h2>Setup and teardown are part of the occupancy</h2>
 *
 * A hall needing thirty minutes to reset from an examination layout is not free the instant the
 * examination ends. {@link #occupied()} widens the window by the buffers, and <strong>that</strong>
 * is what conflict is tested on — booking against the bare window would let the next booking start
 * while the chairs are still being moved.
 *
 * <p>The buffers are on the window rather than on the space, because they are a property of what is
 * being done rather than of the room: the same hall needs no reset for a two-hour meeting and half an
 * hour for an examination.
 */
public record BookingWindow(Instant start, Instant end, int setupMinutes, int teardownMinutes) {

    /** The longest a single booking may run. A year-long booking is a data-entry error, not a booking. */
    private static final Duration MAX_DURATION = Duration.ofDays(14);

    public BookingWindow {
        Objects.requireNonNull(start, "start is required");
        Objects.requireNonNull(end, "end is required");
        if (!end.isAfter(start)) {
            // Equal is rejected as well as inverted: a zero-length booking occupies nothing, cannot
            // conflict with anything, and would sit in the register looking like a real reservation.
            throw new FacilitiesException.ValidationFailedException(
                    "A booking must end after it starts.");
        }
        if (setupMinutes < 0 || teardownMinutes < 0) {
            throw new IllegalArgumentException("setup and teardown cannot be negative");
        }
        if (Duration.between(start, end).compareTo(MAX_DURATION) > 0) {
            throw new FacilitiesException.ValidationFailedException(
                    "A booking cannot run for more than " + MAX_DURATION.toDays() + " days.");
        }
    }

    public static BookingWindow of(Instant start, Instant end) {
        return new BookingWindow(start, end, 0, 0);
    }

    /**
     * The window the space is genuinely unavailable for: the booking plus its buffers.
     *
     * <p>Every conflict test uses this and never {@code this}. The two differ by exactly the setup
     * and teardown, and a check written against the wrong one fails in the direction nobody notices
     * until a lecturer walks into a room being re-laid.
     */
    public BookingWindow occupied() {
        return setupMinutes == 0 && teardownMinutes == 0
                ? this
                : new BookingWindow(start.minus(Duration.ofMinutes(setupMinutes)),
                        end.plus(Duration.ofMinutes(teardownMinutes)), 0, 0);
    }

    /** Half-open overlap: {@code [aStart, aEnd)} against {@code [bStart, bEnd)}. */
    public boolean overlaps(BookingWindow other) {
        return start.isBefore(other.end()) && other.start().isBefore(end);
    }

    /** {@code true} when this window is entirely in the past at {@code now}. */
    public boolean hasPassed(Instant now) {
        return !end.isAfter(now);
    }

    /** {@code true} when {@code now} falls inside the booked window — not the occupied one. */
    public boolean isRunningAt(Instant now) {
        return !now.isBefore(start) && now.isBefore(end);
    }

    public Duration duration() {
        return Duration.between(start, end);
    }
}
