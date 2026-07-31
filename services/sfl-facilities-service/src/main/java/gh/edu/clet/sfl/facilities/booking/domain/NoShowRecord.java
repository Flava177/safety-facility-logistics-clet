package gh.edu.clet.sfl.facilities.booking.domain;

import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A booking that was held and never used — SRS-SFL-S159-01, "no-show record".
 *
 * <p>Written by the scheduled sweep alongside the status change, rather than inferred later from a
 * {@code NO_SHOW} booking. The reason is what it captures that the status cannot: the space, the
 * window, and <strong>the room-time the booking took out of the diary</strong>. That last figure is
 * the only one that answers the question a no-show policy is written to answer — how much room-time
 * is being lost — and reconstructing it from bookings after the fact means re-deriving it every time
 * somebody asks.
 *
 * <p>It is the full booked duration, not the time elapsed before the sweep released the space. The
 * sweep frees the room after the grace period, so the hall stood empty for twenty minutes; but the
 * three hours were unavailable to everybody else from the moment the booking was made, and it is
 * those three hours a repeat-offender report is about.
 *
 * <p>{@code requestedBy} and the room code are copied rather than joined for the same reason: a
 * report of who repeatedly fails to turn up should not stop working because a booking was archived.
 *
 * @param minutesHeldUnused the booked duration in minutes. What the policy is actually about.
 */
public record NoShowRecord(
        UUID id,
        UUID bookingId,
        String bookingReference,
        String siteCode,
        UUID roomId,
        String roomCode,
        BookingPurpose purpose,
        Instant windowStart,
        Instant windowEnd,
        long minutesHeldUnused,
        String requestedBy,
        Instant recordedAt) {

    public NoShowRecord {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(bookingId, "bookingId is required");
        bookingReference = EstateCodes.normalize(bookingReference);
        siteCode = EstateCodes.normalize(siteCode);
        Objects.requireNonNull(roomId, "roomId is required");
        roomCode = EstateCodes.normalize(roomCode);
        Objects.requireNonNull(windowStart, "windowStart is required");
        Objects.requireNonNull(windowEnd, "windowEnd is required");
        EstateCodes.require(requestedBy, "requestedBy");
        requestedBy = requestedBy.strip();
        Objects.requireNonNull(recordedAt, "recordedAt is required");
    }

    public static NoShowRecord from(Booking booking, Instant at) {
        return new NoShowRecord(UUID.randomUUID(), booking.id(), booking.bookingReference(),
                booking.siteCode(), booking.roomId(), booking.roomCode(), booking.purpose(),
                booking.window().start(), booking.window().end(),
                Duration.between(booking.window().start(), booking.window().end()).toMinutes(),
                booking.requestedBy(), at);
    }
}
