package gh.edu.clet.sfl.facilities.cleaning.infrastructure.integration;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.booking.application.ports.BookingLifecycleObserver;
import gh.edu.clet.sfl.facilities.booking.domain.Booking;
import gh.edu.clet.sfl.facilities.cleaning.application.BookingCleaningService;
import org.springframework.stereotype.Component;

/**
 * S169's implementation of S159's {@link BookingLifecycleObserver} - SRS-SFL-S169-01 "a booking's
 * setup/teardown requirement raises a cleaning task", without manual re-entry.
 *
 * <p>Booking declares the interface and calls it inside the booking's own transaction; it never learns
 * that cleaning exists ({@code CleaningArchitectureTest} holds it to that). This class is the whole of
 * the coupling in the other direction: it turns a {@link Booking} into the snapshot S169 works from and
 * hands it over. It is quick and does not call back into booking, as the observer contract requires.
 */
@Component
public class BookingCleaningObserver implements BookingLifecycleObserver {

    private final BookingCleaningService cleaning;

    public BookingCleaningObserver(BookingCleaningService cleaning) {
        this.cleaning = cleaning;
    }

    @Override
    public void bookingConfirmed(Booking booking, ActorContext actor) {
        cleaning.bookingConfirmed(S159BookingDirectoryAdapter.snapshot(booking), actor);
    }

    @Override
    public void bookingRescheduled(Booking booking, ActorContext actor) {
        cleaning.bookingRescheduled(S159BookingDirectoryAdapter.snapshot(booking), actor);
    }

    @Override
    public void bookingWithdrawn(Booking booking, String reason, ActorContext actor) {
        cleaning.bookingWithdrawn(S159BookingDirectoryAdapter.snapshot(booking), reason, actor);
    }
}
