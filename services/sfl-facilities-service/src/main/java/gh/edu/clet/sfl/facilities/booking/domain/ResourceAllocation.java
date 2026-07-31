package gh.edu.clet.sfl.facilities.booking.domain;

import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A resource attached to a booking, for that booking window.
 *
 * <h2>Why the window is copied rather than reached through the booking</h2>
 *
 * It looks like duplication and is not. The exclusion constraint that stops a projector being in two
 * halls at once has to range over a column on <strong>this</strong> table, and a constraint cannot
 * follow a join. Moving a booking therefore has to move its allocations, which
 * {@code BookingApplicationService.reschedule} does in one transaction — and the copy is what lets
 * the database enforce the rule at all rather than trusting every future caller to check first.
 *
 * <h2>Why {@code exclusive} is stored</h2>
 *
 * An exclusion constraint can say "these two rows may not overlap". It cannot say "the quantities of
 * the overlapping rows must sum to no more than forty". So the two cases are enforced in different
 * places, and this flag is what tells them apart:
 *
 * <ul>
 *   <li><strong>Single-instance resources</strong> — the one projector, the one lectern — are
 *       {@code exclusive} and the database refuses the second allocation outright, under concurrency,
 *       without the application being involved.</li>
 *   <li><strong>Pooled resources</strong> — forty chairs, twelve laptops — are not, and their
 *       arithmetic is done in {@link gh.edu.clet.sfl.facilities.booking.domain.policy.BookingConflictPolicy}
 *       against the allocations already committed.</li>
 * </ul>
 *
 * <p>The gap is real and is recorded rather than papered over: two concurrent requests for the last
 * twenty of forty chairs can both succeed. That is a chair shortage discovered at setup, not a hall
 * double-booked at examination time, and buying the guarantee would mean serialising every booking in
 * the estate behind one lock.
 *
 * @param quantity how many of the resource this booking takes.
 */
public record ResourceAllocation(
        UUID id,
        UUID bookingId,
        UUID resourceId,
        String resourceCode,
        String siteCode,
        BookingWindow window,
        int quantity,
        boolean exclusive,
        boolean releasedWithBooking,
        String allocatedBy,
        Instant allocatedAt) {

    public ResourceAllocation {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(bookingId, "bookingId is required");
        Objects.requireNonNull(resourceId, "resourceId is required");
        resourceCode = EstateCodes.normalize(resourceCode);
        siteCode = EstateCodes.normalize(siteCode);
        Objects.requireNonNull(window, "window is required");
        EstateCodes.require(allocatedBy, "allocatedBy");
        allocatedBy = allocatedBy.strip();
        Objects.requireNonNull(allocatedAt, "allocatedAt is required");
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least one");
        }
    }

    public static ResourceAllocation allocate(UUID id, Booking booking, BookableResource resource,
            int quantity, String actorId, Instant at) {
        return new ResourceAllocation(id, booking.id(), resource.id(), resource.resourceCode(),
                booking.siteCode(), booking.window(), quantity, resource.isExclusive(), false, actorId, at);
    }

    /** Released when the booking it belongs to stops holding the space. */
    public ResourceAllocation release() {
        return releasedWithBooking
                ? this
                : new ResourceAllocation(id, bookingId, resourceId, resourceCode, siteCode, window,
                        quantity, exclusive, true, allocatedBy, allocatedAt);
    }

    /** Moved when its booking moves. The two windows must never disagree. */
    public ResourceAllocation withWindow(BookingWindow newWindow) {
        return new ResourceAllocation(id, bookingId, resourceId, resourceCode, siteCode, newWindow,
                quantity, exclusive, releasedWithBooking, allocatedBy, allocatedAt);
    }

    /** {@code true} when this allocation still holds its resource. */
    public boolean isLive() {
        return !releasedWithBooking;
    }
}
