package gh.edu.clet.sfl.facilities.booking.application.ports;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.booking.domain.Booking;

/**
 * Something outside S159 that needs to know when a booking becomes real, moves, or goes away.
 *
 * <p>Declared here, by booking, for the same reason readiness declares {@code ExternalBlockerPort}:
 * the deeper module states the shape of the notification, and the module that cares implements it.
 * S169 cleaning is the first implementation - a booking asking for teardown cleaning raises its task
 * the moment it is confirmed - and booking never learns that cleaning exists, which the architecture
 * test holds it to.
 *
 * <h2>Same transaction, on purpose</h2>
 *
 * <p>Observers are called inside the booking's own transaction, so a failure in one fails the
 * booking change with it. That is the honest trade. The alternative - confirm the booking and let the
 * cleaning task fail to appear on its own - is exactly the "discovered at five o'clock" gap S169-01
 * exists to close, and it would be silent. A refused confirmation is loud and retryable.
 *
 * <p>An observer must therefore be quick and must not call back into booking. It records what it
 * needs and returns.
 */
public interface BookingLifecycleObserver {

    /** The booking now holds its space with a confirmed status - approved, or needing no approval. */
    void bookingConfirmed(Booking booking, ActorContext actor);

    /** A confirmed booking moved to a new window. Anything scheduled against the old one should follow. */
    default void bookingRescheduled(Booking booking, ActorContext actor) {
    }

    /**
     * The booking will not happen: cancelled, or swept as a no-show.
     *
     * @param reason what the booking itself recorded, so the observer's own records say the same thing
     */
    default void bookingWithdrawn(Booking booking, String reason, ActorContext actor) {
    }
}
