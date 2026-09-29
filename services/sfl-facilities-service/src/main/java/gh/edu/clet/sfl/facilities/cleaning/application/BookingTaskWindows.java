package gh.edu.clet.sfl.facilities.cleaning.application;

import gh.edu.clet.sfl.facilities.cleaning.application.ports.BookingDirectoryPort;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskOrigin;
import java.time.Duration;
import java.time.Instant;

/**
 * Where a booking's setup and teardown cleans fall - SRS-SFL-S169-01, so that "completion can be
 * verified against the actual event time".
 *
 * <ul>
 *   <li><strong>Setup</strong> is due by the booking's <em>occupied</em> start - the start of its setup
 *       buffer, not the booked start - because the people laying out the room arrive then and should
 *       find it clean ({@code CleaningRequirement.SETUP}: "in time for the setup buffer"). It opens one
 *       configured clean-length before that.</li>
 *   <li><strong>Teardown</strong> opens at the booked end and is due by the end of the teardown buffer,
 *       or one configured clean-length after the booked end when the buffer is shorter than a clean.</li>
 * </ul>
 */
final class BookingTaskWindows {

    record Window(Instant start, Instant dueBy) {
    }

    private BookingTaskWindows() {
    }

    static Window of(TaskOrigin origin, BookingDirectoryPort.BookingSnapshot booking, Duration cleanLength) {
        if (origin == TaskOrigin.BOOKING_SETUP) {
            return new Window(booking.occupiedStart().minus(cleanLength), booking.occupiedStart());
        }
        if (origin == TaskOrigin.BOOKING_TEARDOWN) {
            Instant byLength = booking.bookedEnd().plus(cleanLength);
            Instant due = booking.occupiedEnd().isAfter(byLength) ? booking.occupiedEnd() : byLength;
            return new Window(booking.bookedEnd(), due);
        }
        throw new IllegalArgumentException(origin + " is not a booking origin");
    }

    static String title(TaskOrigin origin, BookingDirectoryPort.BookingSnapshot booking) {
        return (origin == TaskOrigin.BOOKING_SETUP ? "Setup clean of " : "Teardown clean of ") + booking.roomCode()
                + " for booking " + booking.bookingReference();
    }
}
