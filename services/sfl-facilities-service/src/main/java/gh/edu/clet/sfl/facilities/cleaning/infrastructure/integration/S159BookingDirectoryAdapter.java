package gh.edu.clet.sfl.facilities.cleaning.infrastructure.integration;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.facilities.booking.application.BookingApplicationService;
import gh.edu.clet.sfl.facilities.booking.domain.Booking;
import gh.edu.clet.sfl.facilities.booking.domain.BookingWindow;
import gh.edu.clet.sfl.facilities.booking.domain.CleaningRequirement;
import gh.edu.clet.sfl.facilities.cleaning.application.ports.BookingDirectoryPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * S169's view of S159 bookings - one of the two classes in the cleaning module allowed to name a booking
 * type (the other is {@link BookingCleaningObserver}).
 *
 * <p>A real in-process call to the real S159 service: booking and cleaning share a deployable, so there
 * is nothing to simulate. The lookup runs as a platform service account, because the question is "does
 * this booking exist" and not "may this caller read it" - the cleaning service checks the booking's site
 * against its own caller's scope after resolving, and row-level security narrows the read underneath to
 * the transaction's scope in any case.
 */
@Component
public class S159BookingDirectoryAdapter implements BookingDirectoryPort {

    private static final SiteScopedPrincipal LOOKUP = new SiteScopedPrincipal("system.cleaning",
            "S169 booking lookup", Set.of(SflRole.SFL_ADMIN), Set.of("*"), true);

    /**
     * Resolved on first use, not at construction. {@code BookingApplicationService} is built with its
     * observers, one of which ({@link BookingCleaningObserver}) leads back here through the cleaning
     * services; a constructor dependency would make that a cycle Spring refuses to start with.
     */
    private final ObjectProvider<BookingApplicationService> bookings;

    public S159BookingDirectoryAdapter(ObjectProvider<BookingApplicationService> bookings) {
        this.bookings = bookings;
    }

    @Override
    public Optional<BookingSnapshot> resolve(UUID bookingId) {
        if (bookingId == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(snapshot(bookings.getObject().findById(bookingId, new ActorContext(LOOKUP, "s169-booking-lookup"),
                    SourceChannel.SYSTEM)));
        } catch (FacilitiesException.RecordNotFoundException absent) {
            return Optional.empty();
        }
    }

    /** Shared with the observer, so both paths read a booking identically. */
    public static BookingSnapshot snapshot(Booking booking) {
        BookingWindow occupied = booking.window().occupied();
        CleaningRequirement requirement = CleaningRequirement.orNone(booking.cleaningRequirement());
        return new BookingSnapshot(booking.id(), booking.bookingReference(), booking.siteCode(), booking.roomId(),
                booking.roomCode(), booking.window().start(), booking.window().end(), occupied.start(), occupied.end(),
                requirement.beforeUse(), requirement.afterUse(), booking.holdsTheSpace());
    }
}
