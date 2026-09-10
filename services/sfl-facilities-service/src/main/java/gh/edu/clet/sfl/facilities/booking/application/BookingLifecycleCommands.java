package gh.edu.clet.sfl.facilities.booking.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.booking.application.ports.BookingRepository;
import gh.edu.clet.sfl.facilities.booking.domain.ApprovalDecision;
import gh.edu.clet.sfl.facilities.booking.domain.BookableResource;
import gh.edu.clet.sfl.facilities.booking.domain.Booking;
import gh.edu.clet.sfl.facilities.booking.domain.BookingApproval;
import gh.edu.clet.sfl.facilities.booking.domain.BookingStatus;
import gh.edu.clet.sfl.facilities.booking.domain.BookingWindow;
import gh.edu.clet.sfl.facilities.booking.domain.ResourceAllocation;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.application.port.IdempotencyPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The five booking lifecycle commands - request, decide, reschedule, transition, cancel - split out
 * of {@link BookingApplicationService} so that class is not both the coordinator every controller
 * and sibling service depends on <em>and</em> the home of every command's full body. See that class's
 * Javadoc for the five-gate request sequence and the concurrency guarantee this relies on.
 *
 * <p>Holds a reference to {@link BookingApplicationService} itself, not a narrower dependency list,
 * because the "internals shared with the other booking services" section of that class - window
 * building, readiness resolution, space/resource conflict checks, allocation, setup tasks,
 * visibility, and the rest - is exactly what these five commands need too, and it must stay on that
 * class: {@code BookingSetupService}, {@code BookableResourceService} and
 * {@code BookingReconciliationService} already depend on it by name. Package-private methods are
 * visible to any class in this package, so calling them through the held reference costs nothing and
 * avoids a second copy of that dependency list here.
 */
final class BookingLifecycleCommands {

    private final BookingApplicationService service;
    private final BookingRepository bookings;
    private final AuditPort audit;
    private final IdempotencyPort idempotency;

    BookingLifecycleCommands(BookingApplicationService service, BookingRepository bookings, AuditPort audit,
            IdempotencyPort idempotency) {
        this.service = service;
        this.bookings = bookings;
        this.audit = audit;
        this.idempotency = idempotency;
    }

    Booking request(BookingCommands.RequestBooking command) {
        ActorContext actor = command.actor();
        FacilityRoom room = service.requireRoom(command.roomId());
        service.authorization().require(actor, SflPermission.FACILITIES_BOOKING_REQUEST, room.siteCode(),
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

        Instant at = service.now();
        BookingWindow window = service.windowFor(room.siteCode(), command.purpose(), command.startsAt(),
                command.endsAt(), command.setupMinutes(), command.teardownMinutes(), at);

        String overrideReason = service.resolveReadiness(actor, room, command.purpose(),
                command.overrideReason(), command.channel());
        service.assertSpaceIsFree(window, room, null);

        Map<UUID, Integer> requested = normaliseRequest(command.resources());
        List<BookableResource> resources = service.requireResources(room.siteCode(), requested.keySet());
        service.assertResourcesAreFree(window, requested, resources, null);

        boolean approvalRequired = service.configuration().approvalRequired(room.siteCode(), command.purpose(),
                window, service.operatingModeOf(room.siteCode()));

        Booking booking = Booking.request(UUID.randomUUID(),
                bookings.nextBookingReference(room.siteCode()), room.siteCode(), room.id(), room.roomCode(),
                command.purpose(), command.title(), command.description(), window,
                command.expectedAttendees(), command.requestedFor(), approvalRequired, overrideReason,
                actor.actorId(), at, command.channel(), actor.correlationId());
        Booking saved = bookings.saveBooking(booking);

        service.allocate(saved, requested, resources, actor, at, command.channel());
        service.raiseSetupTasks(saved, resources, actor, at, command.channel());

        audit.record(actor, command.channel(), AuditAction.BOOKING_REQUESTED, "Booking",
                saved.id().toString(), saved.siteCode(), null, saved);
        if (saved.wasOverridden()) {
            audit.record(actor, command.channel(), AuditAction.BOOKING_READINESS_OVERRIDDEN, "Booking",
                    saved.id().toString(), saved.siteCode(), null, saved.overrideReason());
        }
        service.publish("sfl.ifimp.booking-requested.v1", saved, actor);

        // A booking needing no approval is confirmed here rather than left REQUESTED. Two audit
        // records for one act is the honest account: the request happened, and the rule that would
        // have sent it to an approver did not apply.
        Booking result = approvalRequired ? saved
                : service.confirmed(saved, null, actor, at, command.channel());

        idempotency.recordResult("request-booking", command.idempotencyKey(),
                idempotency.fingerprint(command.idempotencyPayload()), result.id(), result.siteCode(),
                actor.actorId());
        return result;
    }

    /** Approve or reject a request. SRS-SFL-S159-02. */
    Booking decide(BookingCommands.DecideBooking command) {
        ActorContext actor = command.actor();
        Booking booking = service.requireBooking(command.bookingId());
        service.authorization().require(actor, SflPermission.FACILITIES_BOOKING_APPROVE, booking.siteCode(),
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

        Instant at = service.now();
        BookingApproval approval = bookings.saveApproval(BookingApproval.decide(UUID.randomUUID(), booking,
                command.approve() ? ApprovalDecision.APPROVED : ApprovalDecision.REJECTED, command.reason(),
                actor.actorId(), at));

        if (command.approve()) {
            // Re-checked at approval, not only at request. A hall free when it was asked for on Monday
            // can be taken by an override or a rescheduled booking before Thursday's approval, and
            // confirming into a clash would produce two confirmed bookings for one room.
            FacilityRoom room = service.requireRoom(booking.roomId());
            service.assertSpaceIsFree(booking.window(), room, booking.id());
            return service.confirmed(booking, approval.id(), actor, at, command.channel());
        }

        Booking rejected = bookings.saveBooking(booking.reject(approval.id(), command.reason(),
                actor.actorId(), at, command.channel(), actor.correlationId()));
        service.releaseAllocations(rejected, actor, command.channel());
        service.skipSetupTasks(rejected, "Booking rejected: " + command.reason(), actor, at, command.channel());
        audit.record(actor, command.channel(), AuditAction.BOOKING_REJECTED, "Booking",
                rejected.id().toString(), rejected.siteCode(), booking, rejected);
        service.publish("sfl.ifimp.booking-rejected.v1", rejected, actor);
        return rejected;
    }

    /**
     * Moves a booking, and its allocations with it.
     *
     * <p>One transaction, and it has to be: an allocation left on the old window would hold a
     * projector at a time nothing is happening and release it at a time something is.
     */
    Booking reschedule(BookingCommands.RescheduleBooking command) {
        ActorContext actor = command.actor();
        Booking booking = service.requireBooking(command.bookingId());
        service.requireMayAct(actor, booking, command.channel());
        booking.metadata().requireVersion(command.expectedVersion(), "Booking", booking.id());

        Instant at = service.now();
        FacilityRoom room = service.requireRoom(booking.roomId());
        BookingWindow window = service.windowFor(booking.siteCode(), booking.purpose(), command.startsAt(),
                command.endsAt(),
                command.setupMinutes() == null ? Integer.valueOf(booking.window().setupMinutes())
                        : command.setupMinutes(),
                command.teardownMinutes() == null ? Integer.valueOf(booking.window().teardownMinutes())
                        : command.teardownMinutes(),
                at);

        service.resolveReadiness(actor, room, booking.purpose(), command.overrideReason(), command.channel());
        service.assertSpaceIsFree(window, room, booking.id());

        List<ResourceAllocation> live = bookings
                .findAllocationsForBooking(booking.id()).stream()
                .filter(ResourceAllocation::isLive)
                .toList();
        if (!live.isEmpty()) {
            Map<UUID, Integer> requested = new LinkedHashMap<>();
            live.forEach(allocation -> requested.merge(allocation.resourceId(), allocation.quantity(),
                    Integer::sum));
            service.assertResourcesAreFree(window, requested,
                    service.requireResources(booking.siteCode(), requested.keySet()), booking.id());
        }

        Booking moved = bookings.saveBooking(booking.reschedule(window, actor.actorId(), at,
                command.channel(), actor.correlationId()));
        live.forEach(allocation -> bookings.saveAllocation(allocation.withWindow(window)));

        audit.record(actor, command.channel(), AuditAction.BOOKING_RESCHEDULED, "Booking",
                moved.id().toString(), moved.siteCode(), booking, moved);
        service.publish("sfl.ifimp.booking-rescheduled.v1", moved, actor);
        return moved;
    }

    /** Start or complete. Taking up your own booking is not a privileged act; taking up somebody else's is. */
    Booking transition(BookingCommands.TransitionBooking command) {
        ActorContext actor = command.actor();
        Booking booking = service.requireBooking(command.bookingId());
        service.requireMayAct(actor, booking, command.channel());
        booking.metadata().requireVersion(command.expectedVersion(), "Booking", booking.id());

        Instant at = service.now();
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
            service.releaseAllocations(saved, actor, command.channel());
        }
        audit.record(actor, command.channel(), action, "Booking", saved.id().toString(), saved.siteCode(),
                booking, saved);
        service.publish("sfl.ifimp.booking-" + command.transition().name().toLowerCase(Locale.ROOT) + ".v1",
                saved, actor);
        return saved;
    }

    Booking cancel(BookingCommands.CancelBooking command) {
        ActorContext actor = command.actor();
        Booking booking = service.requireBooking(command.bookingId());
        service.requireMayAct(actor, booking, command.channel());
        booking.metadata().requireVersion(command.expectedVersion(), "Booking", booking.id());

        Instant at = service.now();
        Booking cancelled = bookings.saveBooking(booking.cancel(command.reason(), actor.actorId(), at,
                command.channel(), actor.correlationId()));
        service.releaseAllocations(cancelled, actor, command.channel());
        service.skipSetupTasks(cancelled, "Booking cancelled: " + command.reason(), actor, at, command.channel());

        audit.record(actor, command.channel(), AuditAction.BOOKING_CANCELLED, "Booking",
                cancelled.id().toString(), cancelled.siteCode(), booking, cancelled);
        service.publish("sfl.ifimp.booking-cancelled.v1", cancelled, actor);
        return cancelled;
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
