package gh.edu.clet.sfl.facilities.booking.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.booking.application.ports.BookingRepository;
import gh.edu.clet.sfl.facilities.booking.domain.BookableResource;
import gh.edu.clet.sfl.facilities.booking.domain.Booking;
import gh.edu.clet.sfl.facilities.booking.domain.BookingApproval;
import gh.edu.clet.sfl.facilities.booking.domain.NoShowRecord;
import gh.edu.clet.sfl.facilities.booking.domain.ResourceAllocation;
import gh.edu.clet.sfl.facilities.booking.domain.ResourceCategory;
import gh.edu.clet.sfl.facilities.booking.domain.SetupTask;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/**
 * The one adapter behind {@link BookingRepository}.
 *
 * <h2>Translating the exclusion constraint</h2>
 *
 * This is the class where the double-booking guarantee becomes a message somebody can read.
 *
 * <p>{@code BookingApplicationService} checks for conflicts before it writes, and that check is
 * genuinely useful — it names the booking that has the hall. It is also not a guarantee: two requests
 * can both read an empty diary before either writes. The guarantee is the {@code GIST} exclusion
 * constraint in V10, and when it fires the loser gets a PostgreSQL error naming an index.
 *
 * <p>{@link #saveBooking} and {@link #saveAllocation} therefore flush inside a {@code try} and
 * translate that error into {@link FacilitiesException.BookingConflictException} — the same exception
 * the pre-write check raises. Losing a race and asking late become one error state, which is the
 * right outcome: from the requester's side they are the same event, and the difference is a detail of
 * how close together two people pressed a button.
 *
 * <p>{@code saveAndFlush} rather than {@code save} is load-bearing. With a deferred flush the
 * violation would surface at commit, outside this method, as an opaque transaction failure that no
 * amount of catching here would reach.
 */
@Repository
public class JpaBookingRepositoryAdapter implements BookingRepository {

    /** The V10 constraint names. Changing either name means changing this list. */
    private static final String SPACE_CONSTRAINT = "ux_bookings_no_double_booking";
    private static final String RESOURCE_CONSTRAINT = "ux_booking_allocations_exclusive";

    /**
     * Stand-ins for "no lower bound" and "no upper bound" on a time-ranged search.
     *
     * <p>Every other optional filter is passed as a null and tested with {@code :p is null} in JPQL.
     * The temporal ones cannot be: PostgreSQL rejects such a query with <em>"could not determine data
     * type of parameter"</em>, because {@code IS NULL} gives the planner nothing to infer from and
     * pgjdbc sends {@code UNSPECIFIED} for a null {@code Instant}. String, UUID and enum parameters
     * carry a concrete OID, which is why the idiom works for them.
     *
     * <p>{@code Instant.MIN} and {@code Instant.MAX} are not usable here — both fall outside what
     * {@code timestamptz} can represent. These two are far enough outside any real booking to be
     * unbounded in practice and inside the column's range.
     */
    private static final Instant UNBOUNDED_FROM = Instant.parse("1900-01-01T00:00:00Z");
    private static final Instant UNBOUNDED_TO = Instant.parse("9999-12-31T00:00:00Z");

    /** Keeps space and resource advisory-lock keys in separate namespaces. Arbitrary, and fixed. */
    private static final long SPACE_LOCK_NAMESPACE = 0x5346_4C53_5041_4345L;
    private static final long RESOURCE_LOCK_NAMESPACE = 0x5346_4C52_4553_5243L;

    private final JpaBookingJpaRepository bookings;
    private final JpaBookableResourceJpaRepository resources;
    private final JpaResourceAllocationJpaRepository allocations;
    private final JpaBookingApprovalJpaRepository approvals;
    private final JpaSetupTaskJpaRepository setupTasks;
    private final JpaNoShowJpaRepository noShows;

    public JpaBookingRepositoryAdapter(JpaBookingJpaRepository bookings,
            JpaBookableResourceJpaRepository resources, JpaResourceAllocationJpaRepository allocations,
            JpaBookingApprovalJpaRepository approvals, JpaSetupTaskJpaRepository setupTasks,
            JpaNoShowJpaRepository noShows) {
        this.bookings = bookings;
        this.resources = resources;
        this.allocations = allocations;
        this.approvals = approvals;
        this.setupTasks = setupTasks;
        this.noShows = noShows;
    }

    // ---- concurrency --------------------------------------------------------------------------

    @Override
    public void lockSpace(UUID roomId) {
        bookings.acquireAdvisoryLock(lockKey(roomId, SPACE_LOCK_NAMESPACE));
    }

    @Override
    public void lockResources(Collection<UUID> resourceIds) {
        // Sorted, so two transactions wanting the same pair take them in the same order. Unsorted,
        // one could hold the projector and want the lectern while the other holds the lectern and
        // wants the projector — which is the deadlock this whole mechanism exists to prevent, moved
        // one level up rather than removed.
        resourceIds.stream()
                .sorted()
                .forEach(id -> bookings.acquireAdvisoryLock(lockKey(id, RESOURCE_LOCK_NAMESPACE)));
    }

    /**
     * A 64-bit advisory-lock key from a UUID.
     *
     * <p>Folding 128 bits into 64 can collide, and the consequence of a collision is that two
     * unrelated spaces queue behind each other for the length of one booking write. That is a
     * millisecond of lost parallelism at odds of about one in nine million per pair, against the
     * alternative of a lock table to maintain.
     *
     * <p>The namespace keeps space keys and resource keys apart, so a room and a projector whose
     * folded ids happen to match do not serialise each other.
     */
    private static long lockKey(UUID id, long namespace) {
        return id.getMostSignificantBits() ^ id.getLeastSignificantBits() ^ namespace;
    }

    // ---- bookings -----------------------------------------------------------------------------

    @Override
    public Booking saveBooking(Booking booking) {
        BookingRecord record = bookings.findById(booking.id()).orElseGet(BookingRecord::new);
        record.apply(booking);
        try {
            return bookings.saveAndFlush(record).toDomain();
        } catch (DataAccessException failure) {
            throw translate(failure, SPACE_CONSTRAINT,
                    booking.roomCode() + " is already booked for part of that window."
                            + " Somebody took it while this request was being made.");
        }
    }

    @Override
    public Optional<Booking> findBooking(UUID id) {
        return bookings.findById(id).map(BookingRecord::toDomain);
    }

    @Override
    public Optional<Booking> findBookingByReference(String bookingReference) {
        return bookings.findByBookingReference(normalize(bookingReference)).map(BookingRecord::toDomain);
    }

    @Override
    public List<Booking> findBookings(BookingQuery query) {
        return bookings.search(normalize(query.siteCode()), query.roomId(), query.status(), query.purpose(),
                        query.requestedBy(), from(query.from()), to(query.to()), query.liveOnly(),
                        query.onReadinessHold(), page(query.limit())).stream()
                .map(BookingRecord::toDomain)
                .toList();
    }

    @Override
    public List<Booking> findHoldingBookings(UUID roomId, Instant from, Instant to, UUID excluding) {
        return bookings.findHolding(roomId, from, to, excluding).stream()
                .map(BookingRecord::toDomain)
                .toList();
    }

    @Override
    public List<UUID> findHeldRoomIds(String siteCode, Instant from, Instant to) {
        return bookings.findHeldRoomIds(normalize(siteCode), from, to);
    }

    @Override
    public List<Booking> findUpcomingForRoom(UUID roomId, Instant from, int limit) {
        return bookings.findUpcomingForRoom(roomId, from, page(limit)).stream()
                .map(BookingRecord::toDomain)
                .toList();
    }

    @Override
    public List<Booking> findUpcoming(Instant from, int limit) {
        return bookings.findUpcoming(from, page(limit)).stream().map(BookingRecord::toDomain).toList();
    }

    @Override
    public List<Booking> findNoShowCandidates(Instant startedBefore, int limit) {
        return bookings.findNoShowCandidates(startedBefore, page(limit)).stream()
                .map(BookingRecord::toDomain)
                .toList();
    }

    @Override
    public String nextBookingReference(String siteCode) {
        return "BK-" + normalize(siteCode) + "-" + String.format("%06d", bookings.nextBookingSequence());
    }

    @Override
    public BookingCounts countBookings(String siteCode, Instant asOf) {
        String site = normalize(siteCode);
        if (site == null) {
            return new BookingCounts(0, 0, 0, 0);
        }
        return new BookingCounts(
                (int) bookings.countUpcoming(site, asOf),
                (int) bookings.countAwaitingApproval(site),
                (int) bookings.countOnReadinessHold(site, asOf),
                // Thirty days, matching the window a no-show policy is normally written over. Fixed
                // rather than configured: it is a reporting horizon on one tile, not a rule.
                (int) noShows.countSince(site, asOf.minus(Duration.ofDays(30))));
    }

    // ---- resources ----------------------------------------------------------------------------

    @Override
    public BookableResource saveResource(BookableResource resource) {
        BookableResourceRecord record = resources.findById(resource.id())
                .orElseGet(BookableResourceRecord::new);
        record.apply(resource);
        return resources.save(record).toDomain();
    }

    @Override
    public Optional<BookableResource> findResource(UUID id) {
        return resources.findById(id).map(BookableResourceRecord::toDomain);
    }

    @Override
    public Optional<BookableResource> findResourceByCode(String siteCode, String resourceCode) {
        return resources.findBySiteCodeAndResourceCode(normalize(siteCode), normalize(resourceCode))
                .map(BookableResourceRecord::toDomain);
    }

    @Override
    public List<BookableResource> findResources(String siteCode, ResourceCategory category) {
        return resources.search(normalize(siteCode), category).stream()
                .map(BookableResourceRecord::toDomain)
                .toList();
    }

    @Override
    public List<BookableResource> findResourcesByIds(Collection<UUID> ids) {
        return ids.isEmpty() ? List.of()
                : resources.findByIdIn(ids).stream().map(BookableResourceRecord::toDomain).toList();
    }

    // ---- allocations --------------------------------------------------------------------------

    @Override
    public ResourceAllocation saveAllocation(ResourceAllocation allocation) {
        ResourceAllocationRecord record = allocations.findById(allocation.id())
                .orElseGet(ResourceAllocationRecord::new);
        record.apply(allocation);
        try {
            return allocations.saveAndFlush(record).toDomain();
        } catch (DataAccessException failure) {
            throw translate(failure, RESOURCE_CONSTRAINT,
                    allocation.resourceCode() + " is committed elsewhere for part of that window.");
        }
    }

    @Override
    public Optional<ResourceAllocation> findAllocation(UUID id) {
        return allocations.findById(id).map(ResourceAllocationRecord::toDomain);
    }

    @Override
    public List<ResourceAllocation> findAllocationsForBooking(UUID bookingId) {
        return allocations.findByBookingIdOrderByAllocatedAtAsc(bookingId).stream()
                .map(ResourceAllocationRecord::toDomain)
                .toList();
    }

    @Override
    public List<ResourceAllocation> findLiveAllocations(Collection<UUID> resourceIds, Instant from,
            Instant to) {
        return resourceIds.isEmpty() ? List.of()
                : allocations.findLive(resourceIds, from, to).stream()
                        .map(ResourceAllocationRecord::toDomain)
                        .toList();
    }

    // ---- approvals ----------------------------------------------------------------------------

    @Override
    public BookingApproval saveApproval(BookingApproval approval) {
        BookingApprovalRecord record = approvals.findById(approval.id())
                .orElseGet(BookingApprovalRecord::new);
        record.apply(approval);
        return approvals.save(record).toDomain();
    }

    @Override
    public List<BookingApproval> findApprovals(UUID bookingId) {
        return approvals.findByBookingIdOrderByDecidedAtAsc(bookingId).stream()
                .map(BookingApprovalRecord::toDomain)
                .toList();
    }

    // ---- setup tasks --------------------------------------------------------------------------

    @Override
    public SetupTask saveSetupTask(SetupTask task) {
        SetupTaskRecord record = setupTasks.findById(task.id()).orElseGet(SetupTaskRecord::new);
        record.apply(task);
        return setupTasks.save(record).toDomain();
    }

    @Override
    public Optional<SetupTask> findSetupTask(UUID id) {
        return setupTasks.findById(id).map(SetupTaskRecord::toDomain);
    }

    @Override
    public List<SetupTask> findSetupTasksForBooking(UUID bookingId) {
        return setupTasks.findByBookingIdOrderByDueByAsc(bookingId).stream()
                .map(SetupTaskRecord::toDomain)
                .toList();
    }

    @Override
    public List<SetupTask> findPendingSetupTasks(String siteCode, Instant dueBefore, int limit) {
        return setupTasks.findPending(normalize(siteCode), dueBefore, page(limit)).stream()
                .map(SetupTaskRecord::toDomain)
                .toList();
    }

    // ---- no-shows -----------------------------------------------------------------------------

    @Override
    public NoShowRecord saveNoShow(NoShowRecord record) {
        NoShowRecordEntity entity = noShows.findById(record.id()).orElseGet(NoShowRecordEntity::new);
        entity.apply(record);
        return noShows.save(entity).toDomain();
    }

    @Override
    public List<NoShowRecord> findNoShows(String siteCode, String requestedBy, Instant from, Instant to,
            int limit) {
        return noShows.search(normalize(siteCode), requestedBy, from(from), to(to), page(limit)).stream()
                .map(NoShowRecordEntity::toDomain)
                .toList();
    }

    // ---- internals ----------------------------------------------------------------------------

    /**
     * Turns a database refusal into the domain error it means, or rethrows.
     *
     * <p>Two shapes reach here, and both mean "somebody else has that slot":
     *
     * <ul>
     *   <li><strong>The exclusion constraint fired.</strong> Matched on the constraint name found
     *       anywhere in the exception chain. Coarser than unwrapping a {@code PSQLException} for its
     *       {@code SQLSTATE}, and deliberately so — that would make this the only class in the
     *       service that cares which driver is underneath. The names are ours and distinctive.</li>
     *   <li><strong>A deadlock.</strong> Two transactions each insert a row for the same space, then
     *       each has to check the constraint against the other's uncommitted row, and they wait on
     *       each other; PostgreSQL aborts an arbitrary victim with {@code SQLSTATE 40P01}. The
     *       advisory lock taken in {@code lockSpace} makes this rare, and rare is not never — a hash
     *       collision or a resource taken in an unusual order can still produce one. Untranslated it
     *       reaches the requester as a 500, which is the wrong answer to "can I have the hall?".</li>
     * </ul>
     *
     * <p>The deadlock case is a small lie in one direction: the victim is told the slot is taken when
     * the transaction it lost to might itself roll back. The requester retries and gets it. Telling
     * them the server broke would leave them with nothing to do.
     *
     * <p>Anything else is rethrown untouched. A violation of some other constraint is a bug, and
     * reporting it as "already booked" would send whoever investigates it to the wrong place.
     */
    private static RuntimeException translate(DataAccessException failure, String constraintName,
            String message) {
        if (failure instanceof CannotAcquireLockException) {
            return new FacilitiesException.BookingConflictException(
                    message + " Another request reached it at the same moment; try again.");
        }
        if (!(failure instanceof DataIntegrityViolationException)) {
            return failure;
        }
        Throwable cursor = failure;
        while (cursor != null) {
            String text = cursor.getMessage();
            if (text != null && text.toLowerCase(Locale.ROOT).contains(constraintName)) {
                return new FacilitiesException.BookingConflictException(message);
            }
            cursor = cursor.getCause();
        }
        return failure;
    }

    private static Instant from(Instant value) {
        return value == null ? UNBOUNDED_FROM : value;
    }

    private static Instant to(Instant value) {
        return value == null ? UNBOUNDED_TO : value;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip().toUpperCase(Locale.ROOT);
    }

    private static PageRequest page(int limit) {
        return PageRequest.of(0, Math.max(1, Math.min(limit, 500)));
    }
}
