package gh.edu.clet.sfl.facilities.cleaning.application.ports;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The S159 bookings, as S169 needs to see them - SRS-SFL-S169-01.
 *
 * <p>Declared here and implemented in {@code infrastructure.integration} so the cleaning services never
 * name a booking type, and a test can stand in a double. The one question it answers is "does this
 * booking exist, and what window does it hold" - which is what "a booking-triggered task carries a
 * back-reference to its originating S159 booking" needs checked before a task may claim one.
 */
public interface BookingDirectoryPort {

    /**
     * A booking reduced to what cleaning uses. Also what the lifecycle observer hands S169, so the
     * observer path and the API path build tasks from the same shape.
     *
     * @param occupiedStart the booked start less its setup buffer - a setup clean is due by this
     * @param occupiedEnd the booked end plus its teardown buffer
     * @param beforeUse the booking asked for a setup clean
     * @param afterUse the booking asked for a teardown clean
     * @param live the booking still holds its space (requested, confirmed or in use)
     */
    record BookingSnapshot(UUID bookingId, String bookingReference, String siteCode, UUID roomId, String roomCode,
            Instant bookedStart, Instant bookedEnd, Instant occupiedStart, Instant occupiedEnd, boolean beforeUse,
            boolean afterUse, boolean live) {
    }

    /** The booking, or empty when S159 has no such booking - the "unresolvable reference" of S169-01. */
    Optional<BookingSnapshot> resolve(UUID bookingId);
}
