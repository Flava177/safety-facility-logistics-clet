package gh.edu.clet.sfl.facilities.booking.application.ports;

import gh.edu.clet.sfl.facilities.booking.domain.BookableResource;
import gh.edu.clet.sfl.facilities.booking.domain.Booking;
import gh.edu.clet.sfl.facilities.booking.domain.BookingApproval;
import gh.edu.clet.sfl.facilities.booking.domain.BookingPurpose;
import gh.edu.clet.sfl.facilities.booking.domain.BookingStatus;
import gh.edu.clet.sfl.facilities.booking.domain.NoShowRecord;
import gh.edu.clet.sfl.facilities.booking.domain.ResourceAllocation;
import gh.edu.clet.sfl.facilities.booking.domain.ResourceCategory;
import gh.edu.clet.sfl.facilities.booking.domain.SetupTask;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The booking module's outbound persistence port — SRS-SFL-S159-01.
 *
 * <p>One port for the module, for the same reason {@code FacilitiesRepository} is one port for the
 * estate: a booking, its allocations, its approval and its setup tasks are written in a single
 * transaction and read together on every screen. Five ports would mean five injections to express one
 * use case.
 *
 * <p>Pagination and ordering are expressed as plain parameters. {@code Pageable} is an infrastructure
 * type the application layer must not import, and the ArchUnit boundary test enforces it.
 */
public interface BookingRepository {

    /** Filters for the booking search. Any null field means "no filter". */
    record BookingQuery(
            String siteCode,
            UUID roomId,
            BookingStatus status,
            BookingPurpose purpose,
            String requestedBy,
            Instant from,
            Instant to,
            Boolean liveOnly,
            Boolean onReadinessHold,
            int limit) {
    }

    /** The counts the dashboard and the booking landing page read. */
    record BookingCounts(int upcoming, int awaitingApproval, int onReadinessHold, int recentNoShows) {
    }

    // ---- concurrency --------------------------------------------------------------------------

    /**
     * Serialises concurrent bookings of one space, for the rest of the caller's transaction.
     *
     * <p>Held for correctness by the database's exclusion constraint, this exists for the
     * <em>error message</em>. Without it, sixteen simultaneous requests for one hall behave like
     * this: each inserts its row, each then has to check the constraint against the others' still
     * uncommitted rows, and they end up waiting on each other in a cycle. PostgreSQL detects the
     * deadlock and aborts an arbitrary victim with {@code SQLSTATE 40P01} — which reaches the caller
     * as a 500, not as "that hall is taken". Measured: one success and fifteen server errors.
     *
     * <p>Taking this first makes same-space requests queue, so the second one through reads a diary
     * that already contains the first and is refused readably by
     * {@code BookingConflictPolicy}. Requests for different spaces are unaffected and still run in
     * parallel, which is the whole reason the lock is per space rather than a table lock.
     *
     * <p>Released when the transaction ends, however it ends. There is nothing to unlock and no way
     * to leak one.
     */
    void lockSpace(UUID roomId);

    /** The same, for resources. Taken after {@link #lockSpace} and in a stable order. */
    void lockResources(Collection<UUID> resourceIds);

    // ---- bookings -----------------------------------------------------------------------------

    Booking saveBooking(Booking booking);

    Optional<Booking> findBooking(UUID id);

    Optional<Booking> findBookingByReference(String bookingReference);

    List<Booking> findBookings(BookingQuery query);

    /**
     * Bookings that hold {@code roomId} and overlap {@code [from, to)}.
     *
     * <p>The conflict check's input, and the reason the interval is passed rather than a day: a
     * booking that starts on Monday evening and ends on Tuesday morning must be found by a query for
     * either day, which a per-day lookup gets wrong exactly once a week.
     *
     * <p>Implementations must test the <em>occupied</em> window, buffers included, and must filter to
     * the statuses {@code BookingStatus.holdsTheSpace()} names.
     */
    List<Booking> findHoldingBookings(UUID roomId, Instant from, Instant to, UUID excludingBookingId);

    /** Room ids held anywhere in {@code [from, to)} at a site — the availability query's exclusion set. */
    List<UUID> findHeldRoomIds(String siteCode, Instant from, Instant to);

    /** Live bookings on a space from {@code from} forward. The readiness reconciliation's input. */
    List<Booking> findUpcomingForRoom(UUID roomId, Instant from, int limit);

    /** Every live booking across the estate from {@code from} forward, for the reconciliation sweep. */
    List<Booking> findUpcoming(Instant from, int limit);

    /**
     * Confirmed bookings that started before {@code startedBefore} and were never taken up.
     *
     * <p>The caller passes {@code now - grace}; the grace period is a configured rule and does not
     * belong in a query.
     */
    List<Booking> findNoShowCandidates(Instant startedBefore, int limit);

    String nextBookingReference(String siteCode);

    BookingCounts countBookings(String siteCode, Instant asOf);

    // ---- resources ----------------------------------------------------------------------------

    BookableResource saveResource(BookableResource resource);

    Optional<BookableResource> findResource(UUID id);

    Optional<BookableResource> findResourceByCode(String siteCode, String resourceCode);

    List<BookableResource> findResources(String siteCode, ResourceCategory category);

    List<BookableResource> findResourcesByIds(Collection<UUID> ids);

    // ---- allocations --------------------------------------------------------------------------

    ResourceAllocation saveAllocation(ResourceAllocation allocation);

    Optional<ResourceAllocation> findAllocation(UUID id);

    List<ResourceAllocation> findAllocationsForBooking(UUID bookingId);

    /** Unreleased allocations of these resources overlapping {@code [from, to)}. */
    List<ResourceAllocation> findLiveAllocations(Collection<UUID> resourceIds, Instant from, Instant to);

    // ---- approvals ----------------------------------------------------------------------------

    BookingApproval saveApproval(BookingApproval approval);

    List<BookingApproval> findApprovals(UUID bookingId);

    // ---- setup tasks --------------------------------------------------------------------------

    SetupTask saveSetupTask(SetupTask task);

    Optional<SetupTask> findSetupTask(UUID id);

    List<SetupTask> findSetupTasksForBooking(UUID bookingId);

    /** Pending setup tasks due before {@code dueBefore}, oldest first. The turnaround queue. */
    List<SetupTask> findPendingSetupTasks(String siteCode, Instant dueBefore, int limit);

    // ---- no-shows -----------------------------------------------------------------------------

    NoShowRecord saveNoShow(NoShowRecord record);

    List<NoShowRecord> findNoShows(String siteCode, String requestedBy, Instant from, Instant to, int limit);
}
