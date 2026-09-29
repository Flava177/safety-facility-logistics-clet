package gh.edu.clet.sfl.facilities.eventlogistics.infrastructure.integration;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.booking.application.ports.BookingLifecycleObserver;
import gh.edu.clet.sfl.facilities.booking.domain.Booking;
import gh.edu.clet.sfl.facilities.eventlogistics.application.EventResourceRequestService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Tells S173 the moment an S159 booking it placed is confirmed, moved or withdrawn - SRS-SFL-S173-02
 * ("statuses tracked").
 *
 * <p>Without it a VENUE request awaiting S159 approval would read REQUESTED until the next
 * synchronisation sweep, and an approver's "yes" at 16:55 would escalate at 17:00 as if nobody had
 * answered. The sweep remains the safety net.
 *
 * <p>Runs inside the booking's transaction, as every {@link BookingLifecycleObserver} does, and passes
 * the booking's new state in rather than asking S159 for it: an observer must not call back into
 * booking. The service is looked up lazily because S159 is constructed with its observers and S173's
 * services depend on S159 - a constructor cycle otherwise.
 */
@Component
public class EventBookingObserver implements BookingLifecycleObserver {

    private final ObjectProvider<EventResourceRequestService> requests;

    public EventBookingObserver(ObjectProvider<EventResourceRequestService> requests) {
        this.requests = requests;
    }

    @Override
    public void bookingConfirmed(Booking booking, ActorContext actor) {
        changed(booking, actor);
    }

    @Override
    public void bookingWithdrawn(Booking booking, String reason, ActorContext actor) {
        changed(booking, actor);
    }

    private void changed(Booking booking, ActorContext actor) {
        EventResourceRequestService service = requests.getIfAvailable();
        if (service != null) {
            service.bookingChanged(booking.id().toString(), S159BookingGateway.map(booking), actor);
        }
    }
}
