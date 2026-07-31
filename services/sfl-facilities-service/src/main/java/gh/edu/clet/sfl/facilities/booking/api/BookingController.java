package gh.edu.clet.sfl.facilities.booking.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.booking.application.BookableResourceService;
import gh.edu.clet.sfl.facilities.booking.application.BookingApplicationService;
import gh.edu.clet.sfl.facilities.booking.application.BookingCommands;
import gh.edu.clet.sfl.facilities.booking.application.BookingSetupService;
import gh.edu.clet.sfl.facilities.booking.application.ports.BookingRepository;
import gh.edu.clet.sfl.facilities.booking.domain.Booking;
import gh.edu.clet.sfl.facilities.booking.domain.BookingPurpose;
import gh.edu.clet.sfl.facilities.booking.domain.BookingStatus;
import gh.edu.clet.sfl.facilities.shared.api.FacilitiesActorResolver;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Room and resource bookings — SRS-SFL-S159-01, -02.
 *
 * <p>{@code Idempotency-Key} is honoured on the one state-<em>creating</em> POST and nowhere else.
 * Every other operation is a PATCH guarded by the record's version and its state machine, so a repeat
 * is either a no-op or an invalid-transition error, and a key would be ceremony with no failure mode
 * behind it.
 *
 * <p>A request that loses a race for a space comes back {@code 409} with
 * {@code BOOKING_CONFLICT} — the same code as one that simply asked after somebody else. From the
 * requester's side those are the same event.
 */
@RestController
@RequestMapping("/api/v1/facilities/bookings")
@Tag(name = "S159 Bookings", description = "Requesting, approving, moving and cancelling bookings")
public class BookingController {

    private final BookingApplicationService service;
    private final BookableResourceService resources;
    private final BookingSetupService setup;
    private final FacilitiesActorResolver actorResolver;
    private final Clock clock;

    public BookingController(BookingApplicationService service, BookableResourceService resources,
            BookingSetupService setup, FacilitiesActorResolver actorResolver, Clock clock) {
        this.service = service;
        this.resources = resources;
        this.setup = setup;
        this.actorResolver = actorResolver;
        this.clock = clock;
    }

    // ---- lifecycle ----------------------------------------------------------------------------

    @PostMapping
    @Operation(summary = "Request a booking",
            description = "The request holds the space immediately, so a second requester is refused "
                    + "rather than the approver being handed a clash. Confirmed at once where the "
                    + "site's configuration requires no approval.")
    public ResponseEntity<ApiResponse<BookingResponses.BookingResponse>> request(
            @Valid @RequestBody BookingRequests.RequestBooking request, HttpServletRequest http) {
        BookingResponses.BookingResponse result = BookingResponses.BookingResponse.from(
                service.request(new BookingCommands.RequestBooking(request.roomId(), request.purpose(),
                        request.title(), request.description(), request.startsAt(), request.endsAt(),
                        request.setupMinutes(), request.teardownMinutes(), request.expectedAttendees(),
                        request.requestedFor(), request.resources(), request.overrideReason(), actor(http),
                        channel(http), idempotencyKey(http), request)),
                clock);
        return ResponseEntity.created(URI.create("/api/v1/facilities/bookings/" + result.id()))
                .body(ApiResponse.ok(result));
    }

    @PatchMapping("/{bookingId}/decision")
    @Operation(summary = "Approve or reject a booking request",
            description = "SRS-SFL-S159-02. A rejection must carry a reason. An actor may not decide "
                    + "on their own request, administrators included.")
    public ApiResponse<BookingResponses.BookingResponse> decide(@PathVariable UUID bookingId,
            @Valid @RequestBody BookingRequests.DecideBooking request, HttpServletRequest http) {
        return respond(service.decide(new BookingCommands.DecideBooking(bookingId, request.approve(),
                request.reason(), request.expectedVersion(), actor(http), channel(http))));
    }

    @PatchMapping("/{bookingId}/schedule")
    @Operation(summary = "Move a booking to a different window",
            description = "Its resource allocations move with it, in one transaction. Refused once "
                    + "the booking is in use: complete it and raise a new one.")
    public ApiResponse<BookingResponses.BookingResponse> reschedule(@PathVariable UUID bookingId,
            @Valid @RequestBody BookingRequests.RescheduleBooking request, HttpServletRequest http) {
        return respond(service.reschedule(new BookingCommands.RescheduleBooking(bookingId,
                request.startsAt(), request.endsAt(), request.setupMinutes(), request.teardownMinutes(),
                request.overrideReason(), request.expectedVersion(), actor(http), channel(http))));
    }

    @PatchMapping("/{bookingId}/start")
    @Operation(summary = "Somebody has arrived and taken the room",
            description = "Also what stops the no-show sweep releasing the space.")
    public ApiResponse<BookingResponses.BookingResponse> start(@PathVariable UUID bookingId,
            @Valid @RequestBody BookingRequests.TransitionBooking request, HttpServletRequest http) {
        return transition(bookingId, BookingCommands.TransitionBooking.Transition.START, request, http);
    }

    @PatchMapping("/{bookingId}/completion")
    @Operation(summary = "The booking ran and has finished",
            description = "Releases every resource it was holding.")
    public ApiResponse<BookingResponses.BookingResponse> complete(@PathVariable UUID bookingId,
            @Valid @RequestBody BookingRequests.TransitionBooking request, HttpServletRequest http) {
        return transition(bookingId, BookingCommands.TransitionBooking.Transition.COMPLETE, request, http);
    }

    @PatchMapping("/{bookingId}/cancellation")
    @Operation(summary = "Withdraw a booking, with a reason",
            description = "Cancelling your own needs only the permission to request; cancelling "
                    + "somebody else's needs FACILITIES_BOOKING_CANCEL.")
    public ApiResponse<BookingResponses.BookingResponse> cancel(@PathVariable UUID bookingId,
            @Valid @RequestBody BookingRequests.CancelBooking request, HttpServletRequest http) {
        return respond(service.cancel(new BookingCommands.CancelBooking(bookingId, request.reason(),
                request.expectedVersion(), actor(http), channel(http))));
    }

    // ---- queries ------------------------------------------------------------------------------

    @GetMapping
    @Operation(summary = "Search bookings",
            description = "An actor holding only the requester role sees the bookings they requested "
                    + "and no others, whatever the filters say.")
    public ApiResponse<List<BookingResponses.BookingResponse>> search(
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) UUID roomId,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) BookingPurpose purpose,
            @RequestParam(required = false) String requestedBy,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Boolean liveOnly,
            @RequestParam(required = false) Boolean onReadinessHold,
            @RequestParam(defaultValue = "100") int limit,
            HttpServletRequest http) {
        return ApiResponse.ok(service.search(new BookingRepository.BookingQuery(siteCode, roomId, status,
                        purpose, requestedBy, from, to, liveOnly, onReadinessHold, limit), actor(http),
                        channel(http)).stream()
                .map(booking -> BookingResponses.BookingResponse.from(booking, clock))
                .toList());
    }

    @GetMapping("/counts")
    @Operation(summary = "Booking counts for a site, for the dashboard")
    public ApiResponse<BookingResponses.BookingCountsResponse> counts(@RequestParam String siteCode,
            HttpServletRequest http) {
        return ApiResponse.ok(BookingResponses.BookingCountsResponse.from(
                service.counts(siteCode, actor(http), channel(http))));
    }

    @GetMapping("/{bookingId}")
    @Operation(summary = "Read one booking")
    public ApiResponse<BookingResponses.BookingResponse> findById(@PathVariable UUID bookingId,
            HttpServletRequest http) {
        return respond(service.findById(bookingId, actor(http), channel(http)));
    }

    @GetMapping("/{bookingId}/approvals")
    @Operation(summary = "The approval decisions taken on a booking",
            description = "Empty for a booking that needed none, which is what says so — there is no "
                    + "separate flag to fall out of step.")
    public ApiResponse<List<BookingResponses.ApprovalResponse>> approvals(@PathVariable UUID bookingId,
            HttpServletRequest http) {
        return ApiResponse.ok(service.approvals(bookingId, actor(http), channel(http)).stream()
                .map(BookingResponses.ApprovalResponse::from)
                .toList());
    }

    // ---- resources ----------------------------------------------------------------------------

    @GetMapping("/{bookingId}/resources")
    @Operation(summary = "Resources allocated to a booking")
    public ApiResponse<List<BookingResponses.AllocationResponse>> allocations(@PathVariable UUID bookingId,
            HttpServletRequest http) {
        return ApiResponse.ok(service.allocations(bookingId, actor(http), channel(http)).stream()
                .map(BookingResponses.AllocationResponse::from)
                .toList());
    }

    @PostMapping("/{bookingId}/resources")
    @Operation(summary = "Add resources to an existing booking",
            description = "Re-runs the availability arithmetic against everything else committed for "
                    + "the window.")
    public ApiResponse<List<BookingResponses.AllocationResponse>> allocate(@PathVariable UUID bookingId,
            @Valid @RequestBody BookingRequests.AllocateResources request, HttpServletRequest http) {
        return ApiResponse.ok(resources.allocate(new BookingCommands.AllocateResources(bookingId,
                        request.resources(), actor(http), channel(http))).stream()
                .map(BookingResponses.AllocationResponse::from)
                .toList());
    }

    @DeleteMapping("/{bookingId}/resources/{allocationId}")
    @Operation(summary = "Release one resource from a booking")
    public ApiResponse<Void> release(@PathVariable UUID bookingId, @PathVariable UUID allocationId,
            HttpServletRequest http) {
        resources.release(new BookingCommands.ReleaseAllocation(bookingId, allocationId, actor(http),
                channel(http)));
        return ApiResponse.ok(null);
    }

    // ---- setup tasks --------------------------------------------------------------------------

    @GetMapping("/{bookingId}/setup-tasks")
    @Operation(summary = "Turnaround work for a booking")
    public ApiResponse<List<BookingResponses.SetupTaskResponse>> setupTasks(@PathVariable UUID bookingId,
            HttpServletRequest http) {
        return ApiResponse.ok(service.setupTasks(bookingId, actor(http), channel(http)).stream()
                .map(task -> BookingResponses.SetupTaskResponse.from(task, clock))
                .toList());
    }

    @PostMapping("/{bookingId}/setup-tasks")
    @Operation(summary = "Raise turnaround work for a booking",
            description = "Deliberately not an S153 work order: a twenty-minute room turnaround does "
                    + "not belong in the same queue as a failed generator.")
    public ApiResponse<List<BookingResponses.SetupTaskResponse>> createSetupTasks(
            @PathVariable UUID bookingId, @Valid @RequestBody BookingRequests.CreateSetupTasks request,
            HttpServletRequest http) {
        return ApiResponse.ok(setup.create(new BookingCommands.CreateSetupTasks(bookingId,
                        request.tasks().stream()
                                .map(task -> new BookingCommands.CreateSetupTasks.NewSetupTask(
                                        task.description(), task.dueBy(), task.assignedTo()))
                                .toList(),
                        actor(http), channel(http))).stream()
                .map(task -> BookingResponses.SetupTaskResponse.from(task, clock))
                .toList());
    }

    // ---- internals ----------------------------------------------------------------------------

    private ApiResponse<BookingResponses.BookingResponse> transition(UUID bookingId,
            BookingCommands.TransitionBooking.Transition transition,
            BookingRequests.TransitionBooking request, HttpServletRequest http) {
        return respond(service.transition(new BookingCommands.TransitionBooking(bookingId, transition,
                request.notes(), request.expectedVersion(), actor(http), channel(http))));
    }

    private ApiResponse<BookingResponses.BookingResponse> respond(Booking booking) {
        return ApiResponse.ok(BookingResponses.BookingResponse.from(booking, clock));
    }

    private ActorContext actor(HttpServletRequest http) {
        return actorResolver.resolve(http);
    }

    private SourceChannel channel(HttpServletRequest http) {
        return actorResolver.resolveSourceChannel(http);
    }

    private String idempotencyKey(HttpServletRequest http) {
        return actorResolver.resolveIdempotencyKey(http);
    }
}
