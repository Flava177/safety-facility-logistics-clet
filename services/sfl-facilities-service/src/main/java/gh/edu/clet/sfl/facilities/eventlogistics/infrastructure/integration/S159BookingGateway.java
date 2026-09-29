package gh.edu.clet.sfl.facilities.eventlogistics.infrastructure.integration;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.facilities.booking.application.BookableResourceService;
import gh.edu.clet.sfl.facilities.booking.application.BookingApplicationService;
import gh.edu.clet.sfl.facilities.booking.application.BookingAvailabilityService;
import gh.edu.clet.sfl.facilities.booking.application.BookingCommands;
import gh.edu.clet.sfl.facilities.booking.domain.Booking;
import gh.edu.clet.sfl.facilities.booking.domain.BookingPurpose;
import gh.edu.clet.sfl.facilities.booking.domain.BookingStatus;
import gh.edu.clet.sfl.facilities.booking.domain.BookingWindow;
import gh.edu.clet.sfl.facilities.booking.domain.CleaningRequirement;
import gh.edu.clet.sfl.facilities.booking.domain.ResourceAllocation;
import gh.edu.clet.sfl.facilities.eventlogistics.application.ports.BookingGatewayPort;
import gh.edu.clet.sfl.facilities.eventlogistics.application.ports.GatewayOutcome;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.ResourceRequestStatus;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * S173's one door into S159 - SRS-SFL-S173-02.
 *
 * <h2>Who books</h2>
 *
 * <p>A platform service account, as {@code AutomatedWorkOrderIntake} does for S153 and for the same
 * reason: S173 has already authorised the coordinator for {@code FACILITIES_EVENT_COORDINATE}, and a
 * second coordinator picking up the same event must be able to add a projector to the venue booking
 * the first one placed. The coordinator is recorded as the booking's {@code requestedFor}, so S159's
 * diary says who it is for. S159's own rules - approval for EVENT bookings, readiness, the horizon -
 * apply unchanged: a platform account is not an override.
 *
 * <h2>Why availability is asked first</h2>
 *
 * <p>S159 refuses a clash by throwing from a transactional service, and an exception out of a Spring
 * transactional proxy marks the caller's shared transaction rollback-only. Catching it here would
 * leave S173 committing a transaction that can only roll back. So the adapter asks S159's read-only
 * availability model first and turns a clash into a CONFLICTED answer naming the booking that holds
 * the room - the same information S159's exception would carry. Only a true race between the read and
 * the write reaches S159's exception, which then fails the routing call with S159's own
 * {@code BOOKING_CONFLICT}, and the coordinator routes again.
 */
@Component
public class S159BookingGateway implements BookingGatewayPort {

    static final SiteScopedPrincipal PLATFORM = new SiteScopedPrincipal("system.event-logistics",
            "Event logistics (S173)", Set.of(SflRole.SFL_ADMIN), Set.of("*"), true);

    private final BookingApplicationService bookings;
    private final BookableResourceService resources;
    private final BookingAvailabilityService availability;

    public S159BookingGateway(BookingApplicationService bookings, BookableResourceService resources,
            BookingAvailabilityService availability) {
        this.bookings = bookings;
        this.resources = resources;
        this.availability = availability;
    }

    @Override
    public GatewayOutcome requestVenue(VenueRequest request) {
        ActorContext platform = platform(request.correlationId());
        BookingWindow window = BookingWindow.of(request.startsAt(), request.endsAt());
        Optional<BookingAvailabilityService.SpaceAvailability> space = availability.spaces(request.siteCode(), window,
                BookingPurpose.EVENT, null, null, platform, SourceChannel.SYSTEM).stream()
                .filter(candidate -> candidate.room().id().equals(request.roomId()))
                .findFirst();
        if (space.isEmpty()) {
            return GatewayOutcome.conflict("The room is not offered for booking in S159 at " + request.siteCode()
                    + ".");
        }
        if (!space.get().heldBy().isEmpty()) {
            return GatewayOutcome.conflict(space.get().heldBy().stream().map(S159BookingGateway::describe)
                    .collect(Collectors.joining("; ")));
        }
        if (space.get().readinessIssue() != null) {
            return GatewayOutcome.conflict(space.get().readinessDetail());
        }
        Booking booking = bookings.request(new BookingCommands.RequestBooking(request.roomId(), BookingPurpose.EVENT,
                request.title(), "Event venue raised by S173 for request " + request.originReference() + ".",
                request.startsAt(), request.endsAt(), null, null, request.expectedAttendance(), request.requestedFor(),
                Map.of(), null, CleaningRequirement.NONE, platform, SourceChannel.SYSTEM, request.idempotencyKey(),
                request));
        return map(booking);
    }

    @Override
    public GatewayOutcome allocate(AllocationRequest request) {
        ActorContext platform = platform(request.correlationId());
        Booking booking = bookings.findById(UUID.fromString(request.bookingId()), platform, SourceChannel.SYSTEM);
        Optional<BookingAvailabilityService.ResourceAvailability> resource = availability.resources(booking.siteCode(),
                booking.window(), null, platform, SourceChannel.SYSTEM).stream()
                .filter(candidate -> candidate.resource().id().equals(request.resourceId()))
                .findFirst();
        if (resource.isEmpty()) {
            return GatewayOutcome.conflict("Bookable resource " + request.resourceId() + " is not offered at "
                    + booking.siteCode() + ".");
        }
        if (resource.get().free() < request.quantity()) {
            return GatewayOutcome.conflict(resource.get().resource().resourceCode() + ": " + resource.get().free()
                    + " of " + resource.get().resource().quantity() + " free for " + booking.window().start() + " to "
                    + booking.window().end() + "; " + resource.get().committed()
                    + " already committed to other bookings.");
        }
        List<ResourceAllocation> allocations = resources.allocate(new BookingCommands.AllocateResources(booking.id(),
                Map.of(request.resourceId(), request.quantity()), platform, SourceChannel.SYSTEM));
        ResourceAllocation allocation = allocations.stream()
                .filter(candidate -> candidate.resourceId().equals(request.resourceId()))
                .filter(ResourceAllocation::isLive)
                .max(Comparator.comparing(ResourceAllocation::allocatedAt))
                .orElseThrow(() -> new IllegalStateException("S159 reported no allocation for the resource it allocated"));
        return new GatewayOutcome(ResourceRequestStatus.REQUESTED, allocation.id().toString(), booking.id().toString(),
                allocation.resourceCode() + " x" + allocation.quantity() + " on " + booking.bookingReference(), null);
    }

    @Override
    public Optional<GatewayOutcome> bookingState(String bookingId) {
        try {
            return Optional.of(map(bookings.findById(UUID.fromString(bookingId), platform(null),
                    SourceChannel.SYSTEM)));
        } catch (FacilitiesException.RecordNotFoundException missing) {
            return Optional.empty();
        }
    }

    @Override
    public void cancelBooking(String bookingId, String reason, String correlationId) {
        ActorContext platform = platform(correlationId);
        Booking booking = bookings.findById(UUID.fromString(bookingId), platform, SourceChannel.SYSTEM);
        if (booking.status().isTerminal()) {
            return;
        }
        bookings.cancel(new BookingCommands.CancelBooking(booking.id(), reason, null, platform, SourceChannel.SYSTEM));
    }

    @Override
    public void releaseAllocation(String bookingId, String allocationId, String correlationId) {
        resources.release(new BookingCommands.ReleaseAllocation(UUID.fromString(bookingId),
                UUID.fromString(allocationId), platform(correlationId), SourceChannel.SYSTEM));
    }

    /** S159's booking status in S173's vocabulary. Public so the booking observer maps the same way. */
    public static GatewayOutcome map(Booking booking) {
        BookingStatus status = booking.status();
        String reference = booking.id().toString();
        return switch (status) {
            case REQUESTED -> GatewayOutcome.of(ResourceRequestStatus.REQUESTED, reference,
                    booking.bookingReference() + " awaiting S159 approval.");
            case CONFIRMED, IN_USE -> GatewayOutcome.of(ResourceRequestStatus.CONFIRMED, reference,
                    booking.bookingReference() + " confirmed in S159.");
            case COMPLETED -> GatewayOutcome.of(ResourceRequestStatus.FULFILLED, reference,
                    booking.bookingReference() + " completed in S159.");
            case REJECTED, CANCELLED, NO_SHOW -> new GatewayOutcome(ResourceRequestStatus.CONFLICTED, reference, null,
                    booking.bookingReference() + " is " + status + " in S159"
                            + (booking.closureReason() == null ? "." : ": " + booking.closureReason()),
                    booking.bookingReference() + " " + status);
        };
    }

    private static String describe(Booking holder) {
        return holder.bookingReference() + " '" + holder.title() + "' holds " + holder.roomCode() + " "
                + holder.window().start() + " to " + holder.window().end() + " (" + holder.status() + ")";
    }

    private static ActorContext platform(String correlationId) {
        return new ActorContext(PLATFORM, correlationId == null ? UUID.randomUUID().toString() : correlationId);
    }
}
