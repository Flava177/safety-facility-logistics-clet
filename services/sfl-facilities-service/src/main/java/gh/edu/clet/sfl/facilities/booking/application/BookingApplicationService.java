package gh.edu.clet.sfl.facilities.booking.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.facilities.booking.application.ports.BookingRepository;
import gh.edu.clet.sfl.facilities.booking.domain.ApprovalDecision;
import gh.edu.clet.sfl.facilities.booking.domain.BookableResource;
import gh.edu.clet.sfl.facilities.booking.domain.Booking;
import gh.edu.clet.sfl.facilities.booking.domain.BookingApproval;
import gh.edu.clet.sfl.facilities.booking.domain.BookingStatus;
import gh.edu.clet.sfl.facilities.booking.domain.BookingWindow;
import gh.edu.clet.sfl.facilities.booking.domain.ReadinessHoldReason;
import gh.edu.clet.sfl.facilities.booking.domain.ResourceAllocation;
import gh.edu.clet.sfl.facilities.booking.domain.SetupTask;
import gh.edu.clet.sfl.facilities.booking.domain.SetupTaskStatus;
import gh.edu.clet.sfl.facilities.booking.domain.policy.BookingConflictPolicy;
import gh.edu.clet.sfl.facilities.booking.domain.policy.ReadinessHoldPolicy;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.application.port.IdempotencyPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The booking workflow — SRS-SFL-S159-02.
 *
 * <h2>The order the checks run in, which is the design</h2>
 *
 * A request passes five gates and the sequence matters, because each one produces a different message
 * and a requester who is told the wrong one wastes a support call:
 *
 * <ol>
 *   <li><strong>Permission and site.</strong> Refused before anything is read, so a caller cannot
 *       learn from a timing difference whether a room exists.</li>
 *   <li><strong>The window itself.</strong> Inverted, zero-length, over the maximum duration, already
 *       finished, or beyond the booking horizon. All data-entry errors, none of them about the estate.</li>
 *   <li><strong>Readiness.</strong> Whether this space can host this purpose at all — see
 *       {@link ReadinessHoldPolicy}. Overridable, with a reason, by the few roles that hold it.</li>
 *   <li><strong>Space conflict.</strong> Somebody already has the hall.</li>
 *   <li><strong>Resource conflict.</strong> The hall is free but the projector is not.</li>
 * </ol>
 *
 * <p>Readiness deliberately runs before conflict. "That hall is blocked, choose another" is more use
 * than "that hall is taken at eleven" when the hall was never usable in the first place.
 *
 * <h2>What actually guarantees no double-booking</h2>
 *
 * Not gate four. A read-then-write check cannot hold under concurrency however carefully it is
 * written: two requests can both read an empty diary before either writes. The guarantee is the
 * {@code GIST} exclusion constraint in {@code V10}, which the adapter translates back into a
 * {@link FacilitiesException.BookingConflictException} so the loser of a race and somebody who simply
 * asked late get the same error state. Gate four exists to make the ordinary case <em>readable</em> —
 * naming the booking that has the room — not to make it correct.
 *
 * <h2>Whose bookings a requester can see</h2>
 *
 * An actor holding only {@link SflRole#IFIMP_REQUESTER} sees the bookings they requested and no
 * others, narrowed per record in {@link #assertVisible} and {@link #requesterFilter}. The full diary
 * would tell somebody which halls are empty and when, which is not what booking a meeting room earns.
 * Every other facilities-facing role reads the whole site diary, because that is what stops two people
 * planning around the same hall.
 */
@Service
public class BookingApplicationService {

    private final BookingRepository bookings;
    private final FacilitiesRepository facilities;
    private final BookingConfiguration configuration;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;
    private final IdempotencyPort idempotency;
    private final ServiceOutbox outbox;
    private final Clock clock;

    public BookingApplicationService(BookingRepository bookings, FacilitiesRepository facilities,
            BookingConfiguration configuration, FacilitiesAuthorization authorization, AuditPort audit,
            IdempotencyPort idempotency, ServiceOutbox outbox, Clock clock) {
        this.bookings = bookings;
        this.facilities = facilities;
        this.configuration = configuration;
        this.authorization = authorization;
        this.audit = audit;
        this.idempotency = idempotency;
        this.outbox = outbox;
        this.clock = clock;
    }

    // =============================================================================================
    // Commands
    // =============================================================================================

    @Transactional
    public Booking request(BookingCommands.RequestBooking command) {
        ActorContext actor = command.actor();
        FacilityRoom room = requireRoom(command.roomId());
        authorization.require(actor, SflPermission.FACILITIES_BOOKING_REQUEST, room.siteCode(),
                command.channel(), "Booking", "new");

        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            Optional<Booking> replayed = idempotency
                    .findExistingResult("request-booking", command.idempotencyKey(),
                            idempotency.fingerprint(command.idempotencyPayload()))
                    .flatMap(bookings::findBooking);
            if (replayed.isPresent()) {
                return replayed.get();
            }
        }

        Instant at = now();
        BookingWindow window = windowFor(room.siteCode(), command.purpose(), command.startsAt(),
                command.endsAt(), command.setupMinutes(), command.teardownMinutes(), at);

        String overrideReason = resolveReadiness(actor, room, command.purpose(), command.overrideReason(),
                command.channel());
        assertSpaceIsFree(window, room, null);

        Map<UUID, Integer> requested = normaliseRequest(command.resources());
        List<BookableResource> resources = requireResources(room.siteCode(), requested.keySet());
        assertResourcesAreFree(window, requested, resources, null);

        boolean approvalRequired = configuration.approvalRequired(room.siteCode(), command.purpose(), window,
                operatingModeOf(room.siteCode()));

        Booking booking = Booking.request(UUID.randomUUID(),
                bookings.nextBookingReference(room.siteCode()), room.siteCode(), room.id(), room.roomCode(),
                command.purpose(), command.title(), command.description(), window,
                command.expectedAttendees(), command.requestedFor(), approvalRequired, overrideReason,
                actor.actorId(), at, command.channel(), actor.correlationId());
        Booking saved = bookings.saveBooking(booking);

        allocate(saved, requested, resources, actor, at, command.channel());
        raiseSetupTasks(saved, resources, actor, at, command.channel());

        audit.record(actor, command.channel(), AuditAction.BOOKING_REQUESTED, "Booking",
                saved.id().toString(), saved.siteCode(), null, saved);
        if (saved.wasOverridden()) {
            audit.record(actor, command.channel(), AuditAction.BOOKING_READINESS_OVERRIDDEN, "Booking",
                    saved.id().toString(), saved.siteCode(), null, saved.overrideReason());
        }
        publish("sfl.ifimp.booking-requested.v1", saved, actor);

        // A booking needing no approval is confirmed here rather than left REQUESTED. Two audit
        // records for one act is the honest account: the request happened, and the rule that would
        // have sent it to an approver did not apply.
        Booking result = approvalRequired ? saved
                : confirmed(saved, null, actor, at, command.channel());

        idempotency.recordResult("request-booking", command.idempotencyKey(),
                idempotency.fingerprint(command.idempotencyPayload()), result.id(), result.siteCode(),
                actor.actorId());
        return result;
    }

    /** Approve or reject a request. SRS-SFL-S159-02. */
    @Transactional
    public Booking decide(BookingCommands.DecideBooking command) {
        ActorContext actor = command.actor();
        Booking booking = requireBooking(command.bookingId());
        authorization.require(actor, SflPermission.FACILITIES_BOOKING_APPROVE, booking.siteCode(),
                command.channel(), "Booking", booking.id().toString());
        booking.metadata().requireVersion(command.expectedVersion(), "Booking", booking.id());

        if (booking.status() != BookingStatus.REQUESTED) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Only a requested booking can be approved or rejected; this one is "
                            + booking.status() + ".");
        }
        // An approver deciding on their own request is the one thing separation of duties exists to
        // stop, and it is cheap to refuse here. Administrators are not exempt: an admin who needs a
        // room asks somebody else, exactly as a supervisor does.
        if (actor.actorId().equals(booking.requestedBy())) {
            audit.recordDenial(actor, command.channel(), "Booking", booking.id().toString(),
                    booking.siteCode(), "An actor may not approve their own booking request");
            throw new FacilitiesException.UnauthorizedApprovalException(
                    "You cannot approve your own booking request.");
        }

        Instant at = now();
        BookingApproval approval = bookings.saveApproval(BookingApproval.decide(UUID.randomUUID(), booking,
                command.approve() ? ApprovalDecision.APPROVED : ApprovalDecision.REJECTED, command.reason(),
                actor.actorId(), at));

        if (command.approve()) {
            // Re-checked at approval, not only at request. A hall free when it was asked for on Monday
            // can be taken by an override or a rescheduled booking before Thursday's approval, and
            // confirming into a clash would produce two confirmed bookings for one room.
            FacilityRoom room = requireRoom(booking.roomId());
            assertSpaceIsFree(booking.window(), room, booking.id());
            return confirmed(booking, approval.id(), actor, at, command.channel());
        }

        Booking rejected = bookings.saveBooking(booking.reject(approval.id(), command.reason(),
                actor.actorId(), at, command.channel(), actor.correlationId()));
        releaseAllocations(rejected, actor, command.channel());
        skipSetupTasks(rejected, "Booking rejected: " + command.reason(), actor, at, command.channel());
        audit.record(actor, command.channel(), AuditAction.BOOKING_REJECTED, "Booking",
                rejected.id().toString(), rejected.siteCode(), booking, rejected);
        publish("sfl.ifimp.booking-rejected.v1", rejected, actor);
        return rejected;
    }

    /**
     * Moves a booking, and its allocations with it.
     *
     * <p>One transaction, and it has to be: an allocation left on the old window would hold a
     * projector at a time nothing is happening and release it at a time something is.
     */
    @Transactional
    public Booking reschedule(BookingCommands.RescheduleBooking command) {
        ActorContext actor = command.actor();
        Booking booking = requireBooking(command.bookingId());
        requireMayAct(actor, booking, command.channel());
        booking.metadata().requireVersion(command.expectedVersion(), "Booking", booking.id());

        Instant at = now();
        FacilityRoom room = requireRoom(booking.roomId());
        BookingWindow window = windowFor(booking.siteCode(), booking.purpose(), command.startsAt(),
                command.endsAt(),
                command.setupMinutes() == null ? booking.window().setupMinutes() : command.setupMinutes(),
                command.teardownMinutes() == null ? booking.window().teardownMinutes()
                        : command.teardownMinutes(),
                at);

        resolveReadiness(actor, room, booking.purpose(), command.overrideReason(), command.channel());
        assertSpaceIsFree(window, room, booking.id());

        List<ResourceAllocation> live = bookings.findAllocationsForBooking(booking.id()).stream()
                .filter(ResourceAllocation::isLive)
                .toList();
        if (!live.isEmpty()) {
            Map<UUID, Integer> requested = new LinkedHashMap<>();
            live.forEach(allocation -> requested.merge(allocation.resourceId(), allocation.quantity(),
                    Integer::sum));
            assertResourcesAreFree(window, requested,
                    requireResources(booking.siteCode(), requested.keySet()), booking.id());
        }

        Booking moved = bookings.saveBooking(booking.reschedule(window, actor.actorId(), at,
                command.channel(), actor.correlationId()));
        live.forEach(allocation -> bookings.saveAllocation(allocation.withWindow(window)));

        audit.record(actor, command.channel(), AuditAction.BOOKING_RESCHEDULED, "Booking",
                moved.id().toString(), moved.siteCode(), booking, moved);
        publish("sfl.ifimp.booking-rescheduled.v1", moved, actor);
        return moved;
    }

    /** Start or complete. Taking up your own booking is not a privileged act; taking up somebody else's is. */
    @Transactional
    public Booking transition(BookingCommands.TransitionBooking command) {
        ActorContext actor = command.actor();
        Booking booking = requireBooking(command.bookingId());
        requireMayAct(actor, booking, command.channel());
        booking.metadata().requireVersion(command.expectedVersion(), "Booking", booking.id());

        Instant at = now();
        Booking moved = switch (command.transition()) {
            case START -> booking.start(actor.actorId(), at, command.channel(), actor.correlationId());
            case COMPLETE -> booking.complete(command.notes(), actor.actorId(), at, command.channel(),
                    actor.correlationId());
        };
        AuditAction action = switch (command.transition()) {
            case START -> AuditAction.BOOKING_STARTED;
            case COMPLETE -> AuditAction.BOOKING_COMPLETED;
        };

        Booking saved = bookings.saveBooking(moved);
        if (command.transition() == BookingCommands.TransitionBooking.Transition.COMPLETE) {
            releaseAllocations(saved, actor, command.channel());
        }
        audit.record(actor, command.channel(), action, "Booking", saved.id().toString(), saved.siteCode(),
                booking, saved);
        publish("sfl.ifimp.booking-" + command.transition().name().toLowerCase(Locale.ROOT) + ".v1", saved, actor);
        return saved;
    }

    @Transactional
    public Booking cancel(BookingCommands.CancelBooking command) {
        ActorContext actor = command.actor();
        Booking booking = requireBooking(command.bookingId());
        requireMayAct(actor, booking, command.channel());
        booking.metadata().requireVersion(command.expectedVersion(), "Booking", booking.id());

        Instant at = now();
        Booking cancelled = bookings.saveBooking(booking.cancel(command.reason(), actor.actorId(), at,
                command.channel(), actor.correlationId()));
        releaseAllocations(cancelled, actor, command.channel());
        skipSetupTasks(cancelled, "Booking cancelled: " + command.reason(), actor, at, command.channel());

        audit.record(actor, command.channel(), AuditAction.BOOKING_CANCELLED, "Booking",
                cancelled.id().toString(), cancelled.siteCode(), booking, cancelled);
        publish("sfl.ifimp.booking-cancelled.v1", cancelled, actor);
        return cancelled;
    }

    // =============================================================================================
    // Queries
    // =============================================================================================

    @Transactional(readOnly = true)
    public List<Booking> search(BookingRepository.BookingQuery query, ActorContext actor,
            SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_BOOKING_READ, channel, "Booking", "list",
                query.siteCode());
        authorization.requireRequestedSite(actor, query.siteCode(), channel, "Booking");
        String narrowed = requesterFilter(actor);
        BookingRepository.BookingQuery effective = narrowed == null ? query
                : new BookingRepository.BookingQuery(query.siteCode(), query.roomId(), query.status(),
                        query.purpose(), narrowed, query.from(), query.to(), query.liveOnly(),
                        query.onReadinessHold(), query.limit());
        return authorization.filterBySite(actor, bookings.findBookings(effective), Booking::siteCode);
    }

    @Transactional(readOnly = true)
    public Booking findById(UUID id, ActorContext actor, SourceChannel channel) {
        Booking booking = requireBooking(id);
        authorization.require(actor, SflPermission.FACILITIES_BOOKING_READ, booking.siteCode(), channel,
                "Booking", id.toString());
        assertVisible(actor, booking, channel);
        return booking;
    }

    @Transactional(readOnly = true)
    public List<BookingApproval> approvals(UUID bookingId, ActorContext actor, SourceChannel channel) {
        return bookings.findApprovals(findById(bookingId, actor, channel).id());
    }

    @Transactional(readOnly = true)
    public List<ResourceAllocation> allocations(UUID bookingId, ActorContext actor, SourceChannel channel) {
        return bookings.findAllocationsForBooking(findById(bookingId, actor, channel).id());
    }

    @Transactional(readOnly = true)
    public List<SetupTask> setupTasks(UUID bookingId, ActorContext actor, SourceChannel channel) {
        return bookings.findSetupTasksForBooking(findById(bookingId, actor, channel).id());
    }

    @Transactional(readOnly = true)
    public BookingRepository.BookingCounts counts(String siteCode, ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_BOOKING_READ, siteCode, channel, "Booking",
                "counts");
        return bookings.countBookings(siteCode, now());
    }

    // =============================================================================================
    // Internals shared with the other booking services
    // =============================================================================================

    /** Confirms and audits. Extracted because both the no-approval path and approval reach it. */
    Booking confirmed(Booking booking, UUID approvalId, ActorContext actor, Instant at,
            SourceChannel channel) {
        Booking confirmed = bookings.saveBooking(booking.confirm(approvalId, actor.actorId(), at, channel,
                actor.correlationId()));
        audit.record(actor, channel, AuditAction.BOOKING_CONFIRMED, "Booking", confirmed.id().toString(),
                confirmed.siteCode(), booking, confirmed);
        publish("sfl.ifimp.booking-confirmed.v1", confirmed, actor);
        return confirmed;
    }

    /**
     * Builds and validates the window.
     *
     * <p>The horizon and the already-finished test are here rather than on {@link BookingWindow}
     * because both need a clock and a site, and a value object that needed either would stop being
     * testable as arithmetic.
     */
    BookingWindow windowFor(String siteCode, gh.edu.clet.sfl.facilities.booking.domain.BookingPurpose purpose,
            Instant startsAt, Instant endsAt, Integer setupMinutes, Integer teardownMinutes, Instant at) {
        BookingWindow window = new BookingWindow(startsAt, endsAt,
                setupMinutes == null ? configuration.defaultSetupMinutes(siteCode, purpose) : setupMinutes,
                teardownMinutes == null ? configuration.defaultTeardownMinutes(siteCode, purpose)
                        : teardownMinutes);
        if (window.hasPassed(at)) {
            // A start slightly in the past is allowed: somebody recording a session that has just begun
            // is doing something reasonable. A window that has entirely finished is not a booking.
            throw new FacilitiesException.ValidationFailedException(
                    "That window has already finished. A booking cannot be made for the past.");
        }
        Duration horizon = configuration.horizon(siteCode);
        if (window.start().isAfter(at.plus(horizon))) {
            throw new FacilitiesException.ValidationFailedException(
                    "Bookings can be made up to " + horizon.toDays() + " days ahead.");
        }
        return window;
    }

    /**
     * Applies the readiness rule, allowing an override.
     *
     * @return the override reason to store, or {@code null} when the space was fine
     */
    String resolveReadiness(ActorContext actor, FacilityRoom room,
            gh.edu.clet.sfl.facilities.booking.domain.BookingPurpose purpose, String overrideReason,
            SourceChannel channel) {
        ReadinessHoldReason hold = ReadinessHoldPolicy.holdFor(purpose, room.readinessStatus(),
                room.bookable(), room.examinationCapable(), room.lifecycleStatus().isOperational(),
                room.readinessLocked());
        if (hold == null) {
            return null;
        }
        String explanation = ReadinessHoldPolicy.explain(hold, room.roomCode());
        if (overrideReason == null || overrideReason.isBlank()) {
            throw new FacilitiesException.SpaceNotBookableException(explanation);
        }
        if (!authorization.has(actor, SflPermission.FACILITIES_BOOKING_OVERRIDE)) {
            audit.recordDenial(actor, channel, "Booking", room.id().toString(), room.siteCode(),
                    "Booking into a space readiness refuses requires FACILITIES_BOOKING_OVERRIDE");
            throw new FacilitiesException.SpaceNotBookableException(explanation);
        }
        return explanation + " Overridden: " + overrideReason.strip();
    }

    void assertSpaceIsFree(BookingWindow window, FacilityRoom room, UUID excludingBookingId) {
        // Before reading the diary, not after. Two requests that both read an empty diary and then
        // both write are what the database's exclusion constraint is for; this makes them queue
        // instead, so the second one reads a diary containing the first and is refused with a message
        // naming it rather than with a deadlock. See BookingRepository.lockSpace.
        bookings.lockSpace(room.id());
        BookingWindow occupied = window.occupied();
        List<BookingConflictPolicy.Conflict> conflicts = BookingConflictPolicy.spaceConflicts(window,
                room.id(), room.roomCode(), excludingBookingId,
                bookings.findHoldingBookings(room.id(), occupied.start(), occupied.end(),
                        excludingBookingId));
        if (!conflicts.isEmpty()) {
            throw new FacilitiesException.BookingConflictException(
                    BookingConflictPolicy.describe(conflicts));
        }
    }

    void assertResourcesAreFree(BookingWindow window, Map<UUID, Integer> requested,
            Collection<BookableResource> resources, UUID excludingBookingId) {
        if (requested.isEmpty()) {
            return;
        }
        // Always after the space lock, and in a stable order inside. A consistent global lock order is
        // what stops two requests for the same pair of things deadlocking on each other.
        bookings.lockResources(requested.keySet());
        BookingWindow occupied = window.occupied();
        List<BookingConflictPolicy.Conflict> conflicts = BookingConflictPolicy.resourceConflicts(window,
                excludingBookingId, requested, resources,
                bookings.findLiveAllocations(requested.keySet(), occupied.start(), occupied.end()));
        if (!conflicts.isEmpty()) {
            throw new FacilitiesException.ResourceUnavailableException(
                    BookingConflictPolicy.describe(conflicts));
        }
    }

    /** Resolves requested resource ids, refusing anything absent, off-site or retired. */
    List<BookableResource> requireResources(String siteCode, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<BookableResource> found = bookings.findResourcesByIds(ids);
        if (found.size() != ids.size()) {
            throw new FacilitiesException.InvalidParentReferenceException("Bookable resource", ids);
        }
        for (BookableResource resource : found) {
            if (!resource.siteCode().equals(siteCode)) {
                throw new FacilitiesException.ResourceUnavailableException(
                        resource.resourceCode() + " is registered at " + resource.siteCode()
                                + " and cannot be booked at " + siteCode + ".");
            }
            if (!resource.isAllocatable()) {
                throw new FacilitiesException.ResourceUnavailableException(
                        resource.resourceCode() + " is " + resource.lifecycleStatus()
                                + " and is no longer offered.");
            }
        }
        return found;
    }

    void allocate(Booking booking, Map<UUID, Integer> requested, Collection<BookableResource> resources,
            ActorContext actor, Instant at, SourceChannel channel) {
        for (BookableResource resource : resources) {
            int quantity = requested.getOrDefault(resource.id(), 1);
            ResourceAllocation allocation = bookings.saveAllocation(ResourceAllocation.allocate(
                    UUID.randomUUID(), booking, resource, quantity, actor.actorId(), at));
            audit.record(actor, channel, AuditAction.BOOKING_RESOURCE_ALLOCATED, "ResourceAllocation",
                    allocation.id().toString(), booking.siteCode(), null, allocation);
        }
    }

    void releaseAllocations(Booking booking, ActorContext actor, SourceChannel channel) {
        for (ResourceAllocation allocation : bookings.findAllocationsForBooking(booking.id())) {
            if (!allocation.isLive()) {
                continue;
            }
            ResourceAllocation released = bookings.saveAllocation(allocation.release());
            audit.record(actor, channel, AuditAction.BOOKING_RESOURCE_RELEASED, "ResourceAllocation",
                    released.id().toString(), booking.siteCode(), allocation, released);
        }
    }

    /**
     * Raises a turnaround task for each allocated resource that needs putting out.
     *
     * <p>Only for resources declaring {@code requiresSetup}. A booking that takes forty chairs needs
     * somebody to move them; one that takes a laptop does not, and a queue full of "collect laptop"
     * is a queue nobody reads.
     */
    void raiseSetupTasks(Booking booking, Collection<BookableResource> resources, ActorContext actor,
            Instant at, SourceChannel channel) {
        for (BookableResource resource : resources) {
            if (!resource.requiresSetup()) {
                continue;
            }
            SetupTask task = bookings.saveSetupTask(SetupTask.create(UUID.randomUUID(), booking,
                    "Set up " + resource.name() + " (" + resource.resourceCode() + ") in "
                            + booking.roomCode(),
                    booking.window().occupied().start(), null));
            audit.record(actor, channel, AuditAction.BOOKING_SETUP_TASK_CREATED, "SetupTask",
                    task.id().toString(), booking.siteCode(), null, task);
        }
    }

    /** Marks outstanding turnaround work as deliberately not done, when its booking goes away. */
    void skipSetupTasks(Booking booking, String reason, ActorContext actor, Instant at,
            SourceChannel channel) {
        for (SetupTask task : bookings.findSetupTasksForBooking(booking.id())) {
            if (task.status() != SetupTaskStatus.PENDING) {
                continue;
            }
            SetupTask resolved = bookings.saveSetupTask(task.resolve(SetupTaskStatus.SKIPPED, reason,
                    actor.actorId(), at));
            audit.record(actor, channel, AuditAction.BOOKING_SETUP_TASK_RESOLVED, "SetupTask",
                    resolved.id().toString(), booking.siteCode(), task, resolved);
        }
    }

    Booking requireBooking(UUID id) {
        return bookings.findBooking(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Booking", id));
    }

    FacilityRoom requireRoom(UUID roomId) {
        return facilities.findRoom(roomId)
                .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Space", roomId));
    }

    OperatingMode operatingModeOf(String siteCode) {
        return facilities.findSiteByCode(siteCode).map(Site::operatingMode).orElse(OperatingMode.ROUTINE);
    }

    /**
     * The rule for acting on a booking: it is yours, or you hold the permission to touch other
     * people's.
     *
     * <p>{@code FACILITIES_BOOKING_CANCEL} is that permission, and it carries more than its name
     * suggests — it is the "manage somebody else's booking" grant, covering cancellation, moving and
     * marking in use. A second permission that always travelled with it would be ceremony.
     */
    void requireMayAct(ActorContext actor, Booking booking, SourceChannel channel) {
        authorization.requireSite(actor, booking.siteCode(), channel, "Booking", booking.id().toString());
        if (actor.actorId().equals(booking.requestedBy())) {
            return;
        }
        authorization.require(actor, SflPermission.FACILITIES_BOOKING_CANCEL, booking.siteCode(), channel,
                "Booking", booking.id().toString());
    }

    /** Applied to reads as well as writes: a narrowing only one of the two obeys is decorative. */
    void assertVisible(ActorContext actor, Booking booking, SourceChannel channel) {
        String filter = requesterFilter(actor);
        if (filter == null || filter.equals(booking.requestedBy())) {
            return;
        }
        audit.recordDenial(actor, channel, "Booking", booking.id().toString(), booking.siteCode(),
                "A requester may read only the bookings they requested");
        throw new FacilitiesException.UnauthorizedScopeException(
                "You may only view bookings you requested.");
    }

    /** The {@code requestedBy} a query must be narrowed to, or {@code null} for no narrowing. */
    String requesterFilter(ActorContext actor) {
        Set<SflRole> roles = actor.principal().roles();
        boolean onlyRequester = roles.contains(SflRole.IFIMP_REQUESTER)
                && roles.stream().allMatch(role -> role == SflRole.IFIMP_REQUESTER);
        return onlyRequester ? actor.actorId() : null;
    }

    void publish(String eventType, Booking booking, ActorContext actor) {
        outbox.record(eventType, 1, "Booking", booking.id(), booking.siteCode(), actor.correlationId(),
                actor.actorId(), booking);
    }

    Instant now() {
        return clock.instant();
    }

    /** Drops null and non-positive quantities, so a caller sending {@code {id: 0}} gets a clear error. */
    private static Map<UUID, Integer> normaliseRequest(Map<UUID, Integer> requested) {
        if (requested == null || requested.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Integer> cleaned = new LinkedHashMap<>();
        List<UUID> invalid = new ArrayList<>();
        requested.forEach((id, quantity) -> {
            if (id == null) {
                return;
            }
            int amount = quantity == null ? 1 : quantity;
            if (amount < 1) {
                invalid.add(id);
            } else {
                cleaned.put(id, amount);
            }
        });
        if (!invalid.isEmpty()) {
            throw new FacilitiesException.ValidationFailedException(
                    "A resource must be requested in a quantity of at least one: " + invalid);
        }
        return cleaned;
    }
}
