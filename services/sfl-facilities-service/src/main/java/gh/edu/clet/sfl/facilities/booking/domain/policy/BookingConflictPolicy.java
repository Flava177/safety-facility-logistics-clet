package gh.edu.clet.sfl.facilities.booking.domain.policy;

import gh.edu.clet.sfl.facilities.booking.domain.BookableResource;
import gh.edu.clet.sfl.facilities.booking.domain.Booking;
import gh.edu.clet.sfl.facilities.booking.domain.BookingWindow;
import gh.edu.clet.sfl.facilities.booking.domain.ResourceAllocation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Whether a proposed booking clashes with one already held — SRS-SFL-S159-02.
 *
 * <h2>The rule, stated once</h2>
 *
 * Two bookings of the same space clash when their <strong>occupied</strong> windows overlap, treating
 * each as the half-open interval {@code [start, end)}. Both halves of that sentence carry weight:
 *
 * <ul>
 *   <li><strong>Occupied, not booked.</strong> {@link BookingWindow#occupied()} widens by the setup
 *       and teardown buffers. Testing the bare window would let the next booking start while the
 *       chairs are still being moved — the failure nobody notices until a lecturer walks into a room
 *       being re-laid.</li>
 *   <li><strong>Half-open.</strong> A booking ending at 10:00 and one starting at 10:00 do not clash.
 *       Get this wrong in one direction and every back-to-back lecture reports a phantom conflict
 *       until people stop trusting the check; wrong in the other and the hall is double-booked on the
 *       hour, which is exactly when lectures change over.</li>
 * </ul>
 *
 * <h2>Why this exists when the database has an exclusion constraint</h2>
 *
 * The constraint in {@code V10} is the guarantee: it is the only thing that holds when two people
 * press Request in the same second, because no amount of read-then-write in application code can.
 * This class is the <em>explanation</em>. A constraint violation arrives as an opaque SQL error
 * naming an index; a requester needs to be told which booking has the hall and when. So the two are
 * both present and neither is redundant — this refuses readably in the ordinary case, and the
 * constraint refuses correctly in the race.
 *
 * <p>They must agree. The status set here comes from {@link Booking#holdsTheSpace()}, and the
 * constraint's {@code WHERE} clause lists the same three states. If one changes, both change.
 */
public final class BookingConflictPolicy {

    private BookingConflictPolicy() {
    }

    /** What clashed. */
    public enum ConflictKind {
        /** The space itself is already held. */
        SPACE,
        /** A resource is committed elsewhere, or the pool would be oversubscribed. */
        RESOURCE
    }

    /**
     * One clash, in terms the requester can act on.
     *
     * @param subject the space code or resource code, so the message names a thing rather than a UUID
     * @param available for a pooled resource, how many are left; {@code null} for a space clash
     */
    public record Conflict(
            ConflictKind kind,
            UUID bookingId,
            String bookingReference,
            String subject,
            BookingWindow window,
            Integer requested,
            Integer available) {

        public String describe() {
            if (kind == ConflictKind.SPACE) {
                return subject + " is already held by " + bookingReference + " from " + window.start()
                        + " to " + window.end() + ".";
            }
            if (bookingReference != null) {
                return subject + " is committed to " + bookingReference + " from " + window.start()
                        + " to " + window.end() + ".";
            }
            return "Only " + available + " of " + subject + " remain free for this window; "
                    + requested + " were requested.";
        }
    }

    /**
     * Space clashes against the bookings that hold it.
     *
     * <p>{@code existing} is expected to be pre-filtered to the same room; the status test is applied
     * here rather than assumed, because a caller passing an unfiltered list would otherwise get
     * silence rather than an error.
     *
     * @param excludingBookingId the booking being moved, which must not clash with itself
     */
    public static List<Conflict> spaceConflicts(BookingWindow candidate, UUID roomId, String roomCode,
            UUID excludingBookingId, Collection<Booking> existing) {
        Objects.requireNonNull(candidate, "candidate window is required");
        BookingWindow occupied = candidate.occupied();
        List<Conflict> conflicts = new ArrayList<>();
        for (Booking booking : existing) {
            if (!booking.holdsTheSpace()
                    || !booking.roomId().equals(roomId)
                    || booking.id().equals(excludingBookingId)) {
                continue;
            }
            if (occupied.overlaps(booking.window().occupied())) {
                conflicts.add(new Conflict(ConflictKind.SPACE, booking.id(), booking.bookingReference(),
                        roomCode, booking.window(), null, null));
            }
        }
        return List.copyOf(conflicts);
    }

    /**
     * Resource clashes: exclusive resources already committed, and pools that would be oversubscribed.
     *
     * <p>An exclusive resource produces a conflict naming the booking that has it. A pooled resource
     * produces one naming the shortfall, because "the projector is in Hall B" and "you asked for forty
     * chairs and twelve are free" are different problems needing different answers.
     *
     * @param requested resource id to quantity wanted
     * @param resources the resources those ids refer to, already validated to exist
     * @param liveAllocations every unreleased allocation of those resources
     */
    public static List<Conflict> resourceConflicts(BookingWindow candidate, UUID excludingBookingId,
            Map<UUID, Integer> requested, Collection<BookableResource> resources,
            Collection<ResourceAllocation> liveAllocations) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        BookingWindow occupied = candidate.occupied();
        Map<UUID, BookableResource> byId = new HashMap<>();
        resources.forEach(resource -> byId.put(resource.id(), resource));

        List<Conflict> conflicts = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : requested.entrySet()) {
            BookableResource resource = byId.get(entry.getKey());
            if (resource == null) {
                // Not this class's failure to report: an unknown resource is a validation error the
                // service raises before it gets here, with the identifier the caller supplied.
                continue;
            }
            int wanted = entry.getValue() == null ? 1 : entry.getValue();
            int committed = 0;
            ResourceAllocation blocking = null;
            for (ResourceAllocation allocation : liveAllocations) {
                if (!allocation.isLive()
                        || !allocation.resourceId().equals(resource.id())
                        || allocation.bookingId().equals(excludingBookingId)) {
                    continue;
                }
                if (!occupied.overlaps(allocation.window().occupied())) {
                    continue;
                }
                committed += allocation.quantity();
                if (blocking == null) {
                    blocking = allocation;
                }
            }
            if (committed + wanted <= resource.quantity()) {
                continue;
            }
            if (resource.isExclusive() && blocking != null) {
                conflicts.add(new Conflict(ConflictKind.RESOURCE, blocking.bookingId(), null,
                        resource.resourceCode(), blocking.window(), wanted, 0));
            } else {
                conflicts.add(new Conflict(ConflictKind.RESOURCE, null, null, resource.resourceCode(),
                        candidate, wanted, Math.max(0, resource.quantity() - committed)));
            }
        }
        return List.copyOf(conflicts);
    }

    /** Every conflict, joined into the one sentence a requester will read. */
    public static String describe(Collection<Conflict> conflicts) {
        return conflicts.stream().map(Conflict::describe).reduce((a, b) -> a + " " + b).orElse("");
    }
}
