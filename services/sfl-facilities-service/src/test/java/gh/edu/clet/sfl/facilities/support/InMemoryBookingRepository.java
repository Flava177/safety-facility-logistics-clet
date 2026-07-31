package gh.edu.clet.sfl.facilities.support;

import gh.edu.clet.sfl.facilities.booking.application.ports.BookingRepository;
import gh.edu.clet.sfl.facilities.booking.domain.BookableResource;
import gh.edu.clet.sfl.facilities.booking.domain.Booking;
import gh.edu.clet.sfl.facilities.booking.domain.BookingApproval;
import gh.edu.clet.sfl.facilities.booking.domain.BookingStatus;
import gh.edu.clet.sfl.facilities.booking.domain.BookingWindow;
import gh.edu.clet.sfl.facilities.booking.domain.NoShowRecord;
import gh.edu.clet.sfl.facilities.booking.domain.ResourceAllocation;
import gh.edu.clet.sfl.facilities.booking.domain.ResourceCategory;
import gh.edu.clet.sfl.facilities.booking.domain.SetupTask;
import gh.edu.clet.sfl.facilities.booking.domain.SetupTaskStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An in-memory {@link BookingRepository} for the application tests.
 *
 * <h2>What this double does and does not stand in for</h2>
 *
 * It reproduces every <em>query</em> the module makes, including the half-open overlap test, so a
 * rule that reads the wrong rows fails here rather than in an integration test.
 *
 * <p>It deliberately does <strong>not</strong> reproduce the {@code GIST} exclusion constraint. That
 * constraint exists precisely to catch what application code cannot — two requests that both read an
 * empty diary before either writes — and a single-threaded map cannot exhibit that race, so
 * simulating the refusal here would prove nothing and would hide the fact that the guarantee lives in
 * PostgreSQL. The constraint is verified against a real database, and
 * {@code S159MandatoryScenariosTest} pins its {@code WHERE} clause against
 * {@link BookingStatus#holdsTheSpace()} so the two cannot drift.
 */
public class InMemoryBookingRepository implements BookingRepository {

    private final Map<UUID, Booking> bookings = new LinkedHashMap<>();
    private final Map<UUID, BookableResource> resources = new LinkedHashMap<>();
    private final Map<UUID, ResourceAllocation> allocations = new LinkedHashMap<>();
    private final Map<UUID, BookingApproval> approvals = new LinkedHashMap<>();
    private final Map<UUID, SetupTask> setupTasks = new LinkedHashMap<>();
    private final Map<UUID, NoShowRecord> noShows = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    // ---- concurrency --------------------------------------------------------------------------

    /**
     * No-ops, and they have to be.
     *
     * <p>These exist to serialise two transactions racing for one space, and a single-threaded map
     * has no such race to serialise. Simulating a lock here would prove nothing; what the lock is
     * worth was measured against real PostgreSQL, where sixteen simultaneous requests for one hall
     * produced one booking and fifteen deadlock-driven 500s without it.
     */
    @Override
    public void lockSpace(UUID roomId) {
    }

    @Override
    public void lockResources(Collection<UUID> resourceIds) {
    }

    // ---- bookings -----------------------------------------------------------------------------

    @Override
    public Booking saveBooking(Booking booking) {
        bookings.put(booking.id(), booking);
        return booking;
    }

    @Override
    public Optional<Booking> findBooking(UUID id) {
        return Optional.ofNullable(bookings.get(id));
    }

    @Override
    public Optional<Booking> findBookingByReference(String bookingReference) {
        return bookings.values().stream()
                .filter(booking -> booking.bookingReference().equalsIgnoreCase(bookingReference))
                .findFirst();
    }

    @Override
    public List<Booking> findBookings(BookingQuery query) {
        return bookings.values().stream()
                .filter(booking -> query.siteCode() == null
                        || booking.siteCode().equals(normalize(query.siteCode())))
                .filter(booking -> query.roomId() == null || booking.roomId().equals(query.roomId()))
                .filter(booking -> query.status() == null || booking.status() == query.status())
                .filter(booking -> query.purpose() == null || booking.purpose() == query.purpose())
                .filter(booking -> query.requestedBy() == null
                        || booking.requestedBy().equals(query.requestedBy()))
                .filter(booking -> query.from() == null
                        || booking.window().occupied().end().isAfter(query.from()))
                .filter(booking -> query.to() == null
                        || booking.window().occupied().start().isBefore(query.to()))
                .filter(booking -> !Boolean.TRUE.equals(query.liveOnly()) || booking.holdsTheSpace())
                .filter(booking -> query.onReadinessHold() == null
                        || query.onReadinessHold() == (booking.readinessHoldReason() != null))
                .sorted(Comparator.comparing(booking -> booking.window().start()))
                .limit(Math.max(1, query.limit()))
                .toList();
    }

    @Override
    public List<Booking> findHoldingBookings(UUID roomId, Instant from, Instant to, UUID excluding) {
        BookingWindow probe = new BookingWindow(from, to, 0, 0);
        return bookings.values().stream()
                .filter(Booking::holdsTheSpace)
                .filter(booking -> booking.roomId().equals(roomId))
                .filter(booking -> excluding == null || !booking.id().equals(excluding))
                .filter(booking -> probe.overlaps(booking.window().occupied()))
                .sorted(Comparator.comparing(booking -> booking.window().start()))
                .toList();
    }

    @Override
    public List<UUID> findHeldRoomIds(String siteCode, Instant from, Instant to) {
        BookingWindow probe = new BookingWindow(from, to, 0, 0);
        return bookings.values().stream()
                .filter(Booking::holdsTheSpace)
                .filter(booking -> booking.siteCode().equals(normalize(siteCode)))
                .filter(booking -> probe.overlaps(booking.window().occupied()))
                .map(Booking::roomId)
                .distinct()
                .toList();
    }

    @Override
    public List<Booking> findUpcomingForRoom(UUID roomId, Instant from, int limit) {
        return findUpcoming(from, limit).stream()
                .filter(booking -> booking.roomId().equals(roomId))
                .toList();
    }

    @Override
    public List<Booking> findUpcoming(Instant from, int limit) {
        return bookings.values().stream()
                .filter(Booking::holdsTheSpace)
                .filter(booking -> booking.window().occupied().end().isAfter(from))
                .sorted(Comparator.comparing(booking -> booking.window().start()))
                .limit(Math.max(1, limit))
                .toList();
    }

    @Override
    public List<Booking> findNoShowCandidates(Instant startedBefore, int limit) {
        return bookings.values().stream()
                .filter(booking -> booking.status() == BookingStatus.CONFIRMED)
                .filter(booking -> booking.startedAt() == null)
                .filter(booking -> booking.window().start().isBefore(startedBefore))
                .sorted(Comparator.comparing(booking -> booking.window().start()))
                .limit(Math.max(1, limit))
                .toList();
    }

    @Override
    public String nextBookingReference(String siteCode) {
        return "BK-" + normalize(siteCode) + "-" + String.format("%06d", sequence.incrementAndGet());
    }

    @Override
    public BookingCounts countBookings(String siteCode, Instant asOf) {
        String site = normalize(siteCode);
        List<Booking> atSite = bookings.values().stream()
                .filter(booking -> booking.siteCode().equals(site))
                .toList();
        return new BookingCounts(
                (int) atSite.stream().filter(Booking::holdsTheSpace)
                        .filter(booking -> booking.window().occupied().end().isAfter(asOf)).count(),
                (int) atSite.stream().filter(booking -> booking.status() == BookingStatus.REQUESTED).count(),
                (int) atSite.stream().filter(Booking::holdsTheSpace)
                        .filter(booking -> booking.readinessHoldReason() != null).count(),
                (int) noShows.values().stream()
                        .filter(record -> record.siteCode().equals(site))
                        .filter(record -> record.recordedAt().isAfter(asOf.minus(Duration.ofDays(30))))
                        .count());
    }

    // ---- resources ----------------------------------------------------------------------------

    @Override
    public BookableResource saveResource(BookableResource resource) {
        resources.put(resource.id(), resource);
        return resource;
    }

    @Override
    public Optional<BookableResource> findResource(UUID id) {
        return Optional.ofNullable(resources.get(id));
    }

    @Override
    public Optional<BookableResource> findResourceByCode(String siteCode, String resourceCode) {
        return resources.values().stream()
                .filter(resource -> resource.siteCode().equals(normalize(siteCode)))
                .filter(resource -> resource.resourceCode().equals(normalize(resourceCode)))
                .findFirst();
    }

    @Override
    public List<BookableResource> findResources(String siteCode, ResourceCategory category) {
        return resources.values().stream()
                .filter(resource -> siteCode == null || resource.siteCode().equals(normalize(siteCode)))
                .filter(resource -> category == null || resource.category() == category)
                .sorted(Comparator.comparing(BookableResource::resourceCode))
                .toList();
    }

    @Override
    public List<BookableResource> findResourcesByIds(Collection<UUID> ids) {
        return ids.stream().map(resources::get).filter(java.util.Objects::nonNull).toList();
    }

    // ---- allocations --------------------------------------------------------------------------

    @Override
    public ResourceAllocation saveAllocation(ResourceAllocation allocation) {
        allocations.put(allocation.id(), allocation);
        return allocation;
    }

    @Override
    public Optional<ResourceAllocation> findAllocation(UUID id) {
        return Optional.ofNullable(allocations.get(id));
    }

    @Override
    public List<ResourceAllocation> findAllocationsForBooking(UUID bookingId) {
        return allocations.values().stream()
                .filter(allocation -> allocation.bookingId().equals(bookingId))
                .sorted(Comparator.comparing(ResourceAllocation::allocatedAt))
                .toList();
    }

    @Override
    public List<ResourceAllocation> findLiveAllocations(Collection<UUID> resourceIds, Instant from,
            Instant to) {
        BookingWindow probe = new BookingWindow(from, to, 0, 0);
        return allocations.values().stream()
                .filter(ResourceAllocation::isLive)
                .filter(allocation -> resourceIds.contains(allocation.resourceId()))
                .filter(allocation -> probe.overlaps(allocation.window().occupied()))
                .toList();
    }

    // ---- approvals ----------------------------------------------------------------------------

    @Override
    public BookingApproval saveApproval(BookingApproval approval) {
        approvals.put(approval.id(), approval);
        return approval;
    }

    @Override
    public List<BookingApproval> findApprovals(UUID bookingId) {
        return approvals.values().stream()
                .filter(approval -> approval.bookingId().equals(bookingId))
                .sorted(Comparator.comparing(BookingApproval::decidedAt))
                .toList();
    }

    // ---- setup tasks --------------------------------------------------------------------------

    @Override
    public SetupTask saveSetupTask(SetupTask task) {
        setupTasks.put(task.id(), task);
        return task;
    }

    @Override
    public Optional<SetupTask> findSetupTask(UUID id) {
        return Optional.ofNullable(setupTasks.get(id));
    }

    @Override
    public List<SetupTask> findSetupTasksForBooking(UUID bookingId) {
        return setupTasks.values().stream()
                .filter(task -> task.bookingId().equals(bookingId))
                .sorted(Comparator.comparing(SetupTask::dueBy))
                .toList();
    }

    @Override
    public List<SetupTask> findPendingSetupTasks(String siteCode, Instant dueBefore, int limit) {
        return setupTasks.values().stream()
                .filter(task -> task.status() == SetupTaskStatus.PENDING)
                .filter(task -> siteCode == null || task.siteCode().equals(normalize(siteCode)))
                .filter(task -> task.dueBy().isBefore(dueBefore))
                .sorted(Comparator.comparing(SetupTask::dueBy))
                .limit(Math.max(1, limit))
                .toList();
    }

    // ---- no-shows -----------------------------------------------------------------------------

    @Override
    public NoShowRecord saveNoShow(NoShowRecord record) {
        noShows.put(record.id(), record);
        return record;
    }

    @Override
    public List<NoShowRecord> findNoShows(String siteCode, String requestedBy, Instant from, Instant to,
            int limit) {
        List<NoShowRecord> found = new ArrayList<>(noShows.values());
        return found.stream()
                .filter(record -> siteCode == null || record.siteCode().equals(normalize(siteCode)))
                .filter(record -> requestedBy == null || record.requestedBy().equals(requestedBy))
                .filter(record -> from == null || !record.recordedAt().isBefore(from))
                .filter(record -> to == null || record.recordedAt().isBefore(to))
                .sorted(Comparator.comparing(NoShowRecord::recordedAt).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip().toUpperCase(Locale.ROOT);
    }
}
