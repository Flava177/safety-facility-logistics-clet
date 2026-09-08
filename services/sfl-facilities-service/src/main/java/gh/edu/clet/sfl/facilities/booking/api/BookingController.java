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
import gh.edu.clet.sfl.facilities.shared.api.IdempotencyKey;
import gh.edu.clet.sfl.facilities.shared.api.PageResponse;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * Room and resource bookings - SRS-SFL-S159-01, -02.
 *
 * <p>{@code Idempotency-Key} is honoured on the one state-<em>creating</em> POST and nowhere else.
 * Every other operation is a PATCH guarded by the record's version and its state machine, so a repeat
 * is either a no-op or an invalid-transition error, and a key would be ceremony with no failure mode
 * behind it.
 *
 * <p>A request that loses a race for a space comes back {@code 409} with
 * {@code BOOKING_CONFLICT} - the same code as one that simply asked after somebody else. From the
 * requester's side those are the same event.
 */
@RestController
@RequestMapping("/api/v1/facilities/bookings")
@Tag(name = "S159 Bookings", description = "Requesting, approving, moving and cancelling bookings")
public class BookingController {

    private final BookingApplicationService service;
    private final BookableResourceService resources;
    private final BookingSetupService setup;
    private final Clock clock;

    public BookingController(BookingApplicationService service, BookableResourceService resources,
            BookingSetupService setup, Clock clock) {
        this.service = service;
        this.resources = resources;
        this.setup = setup;
        this.clock = clock;
    }

    // ---- lifecycle ----------------------------------------------------------------------------

    @PostMapping
    @Operation(summary = "Request a booking",
            description = "The request holds the space immediately, so a second requester is refused "
                    + "rather than the approver being handed a clash. Confirmed at once where the "
                    + "site's configuration requires no approval.")
    public ResponseEntity<ApiResponse<BookingResponses.BookingResponse>> request(
            @Valid @RequestBody BookingRequests.RequestBooking request, ActorContext actor,
            SourceChannel channel, @IdempotencyKey String idempotencyKey) {
        BookingResponses.BookingResponse result = BookingResponses.BookingResponse.from(
                service.request(new BookingCommands.RequestBooking(request.roomId(), request.purpose(),
                        request.title(), request.description(), request.startsAt(), request.endsAt(),
                        request.setupMinutes(), request.teardownMinutes(), request.expectedAttendees(),
                        request.requestedFor(), request.resources(), request.overrideReason(), actor,
                        channel, idempotencyKey, request)),
                clock);
        return ResponseEntity.created(URI.create("/api/v1/facilities/bookings/" + result.id()))
                .body(ApiResponse.ok(result));
    }

    @PatchMapping("/{bookingId}/decision")
    @Operation(summary = "Approve or reject a booking request",
            description = "SRS-SFL-S159-02. A rejection must carry a reason. An actor may not decide "
                    + "on their own request, administrators included.")
    public ApiResponse<BookingResponses.BookingResponse> decide(@PathVariable UUID bookingId,
            @Valid @RequestBody BookingRequests.DecideBooking request, ActorContext actor, SourceChannel channel) {
        return respond(service.decide(new BookingCommands.DecideBooking(bookingId, request.approve(),
                request.reason(), request.expectedVersion(), actor, channel)));
    }

    @PatchMapping("/{bookingId}/schedule")
    @Operation(summary = "Move a booking to a different window",
            description = "Its resource allocations move with it, in one transaction. Refused once "
                    + "the booking is in use: complete it and raise a new one.")
    public ApiResponse<BookingResponses.BookingResponse> reschedule(@PathVariable UUID bookingId,
            @Valid @RequestBody BookingRequests.RescheduleBooking request, ActorContext actor,
            SourceChannel channel) {
        return respond(service.reschedule(new BookingCommands.RescheduleBooking(bookingId,
                request.startsAt(), request.endsAt(), request.setupMinutes(), request.teardownMinutes(),
                request.overrideReason(), request.expectedVersion(), actor, channel)));
    }

    @PatchMapping("/{bookingId}/start")
    @Operation(summary = "Somebody has arrived and taken the room",
            description = "Also what stops the no-show sweep releasing the space.")
    public ApiResponse<BookingResponses.BookingResponse> start(@PathVariable UUID bookingId,
            @Valid @RequestBody BookingRequests.TransitionBooking request, ActorContext actor,
            SourceChannel channel) {
        return transition(bookingId, BookingCommands.TransitionBooking.Transition.START, request, actor, channel);
    }

    @PatchMapping("/{bookingId}/completion")
    @Operation(summary = "The booking ran and has finished",
            description = "Releases every resource it was holding.")
    public ApiResponse<BookingResponses.BookingResponse> complete(@PathVariable UUID bookingId,
            @Valid @RequestBody BookingRequests.TransitionBooking request, ActorContext actor,
            SourceChannel channel) {
        return transition(bookingId, BookingCommands.TransitionBooking.Transition.COMPLETE, request, actor,
                channel);
    }

    @PatchMapping("/{bookingId}/cancellation")
    @Operation(summary = "Withdraw a booking, with a reason",
            description = "Cancelling your own needs only the permission to request; cancelling "
                    + "somebody else's needs FACILITIES_BOOKING_CANCEL.")
    public ApiResponse<BookingResponses.BookingResponse> cancel(@PathVariable UUID bookingId,
            @Valid @RequestBody BookingRequests.CancelBooking request, ActorContext actor, SourceChannel channel) {
        return respond(service.cancel(new BookingCommands.CancelBooking(bookingId, request.reason(),
                request.expectedVersion(), actor, channel)));
    }

    // ---- queries ------------------------------------------------------------------------------

    @GetMapping
    @Operation(summary = "Search bookings",
            description = "An actor holding only the requester role sees the bookings they requested "
                    + "and no others, whatever the filters say.")
    public ApiResponse<PageResponse<BookingResponses.BookingResponse>> search(
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) UUID roomId,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) BookingPurpose purpose,
            @RequestParam(required = false) String requestedBy,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Boolean liveOnly,
            @RequestParam(required = false) Boolean onReadinessHold,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(PageResponse.from(
                service.search(new BookingRepository.BookingQuery(siteCode, roomId, status, purpose,
                        requestedBy, from, to, liveOnly, onReadinessHold, page, size), actor, channel),
                booking -> BookingResponses.BookingResponse.from(booking, clock)));
    }

    @GetMapping("/counts")
    @Operation(summary = "Booking counts for a site, for the dashboard")
    public ApiResponse<BookingResponses.BookingCountsResponse> counts(@RequestParam String siteCode,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(BookingResponses.BookingCountsResponse.from(
                service.counts(siteCode, actor, channel)));
    }

    @GetMapping("/{bookingId}")
    @Operation(summary = "Read one booking")
    public ApiResponse<BookingResponses.BookingResponse> findById(@PathVariable UUID bookingId,
            ActorContext actor, SourceChannel channel) {
        return respond(service.findById(bookingId, actor, channel));
    }

    @GetMapping("/{bookingId}/approvals")
    @Operation(summary = "The approval decisions taken on a booking",
            description = "Empty for a booking that needed none, which is what says so - there is no "
                    + "separate flag to fall out of step.")
    public ApiResponse<List<BookingResponses.ApprovalResponse>> approvals(@PathVariable UUID bookingId,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(service.approvals(bookingId, actor, channel).stream()
                .map(BookingResponses.ApprovalResponse::from)
                .toList());
    }

    // ---- resources ----------------------------------------------------------------------------

    @GetMapping("/{bookingId}/resources")
    @Operation(summary = "Resources allocated to a booking")
    public ApiResponse<List<BookingResponses.AllocationResponse>> allocations(@PathVariable UUID bookingId,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(service.allocations(bookingId, actor, channel).stream()
                .map(BookingResponses.AllocationResponse::from)
                .toList());
    }

    @PostMapping("/{bookingId}/resources")
    @Operation(summary = "Add resources to an existing booking",
            description = "Re-runs the availability arithmetic against everything else committed for "
                    + "the window.")
    public ApiResponse<List<BookingResponses.AllocationResponse>> allocate(@PathVariable UUID bookingId,
            @Valid @RequestBody BookingRequests.AllocateResources request, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(resources.allocate(new BookingCommands.AllocateResources(bookingId,
                        request.resources(), actor, channel)).stream()
                .map(BookingResponses.AllocationResponse::from)
                .toList());
    }

    @DeleteMapping("/{bookingId}/resources/{allocationId}")
    @Operation(summary = "Release one resource from a booking")
    public ApiResponse<Void> release(@PathVariable UUID bookingId, @PathVariable UUID allocationId,
            ActorContext actor, SourceChannel channel) {
        resources.release(new BookingCommands.ReleaseAllocation(bookingId, allocationId, actor, channel));
        return ApiResponse.ok(null);
    }

    // ---- setup tasks --------------------------------------------------------------------------

    @GetMapping("/{bookingId}/setup-tasks")
    @Operation(summary = "Turnaround work for a booking")
    public ApiResponse<List<BookingResponses.SetupTaskResponse>> setupTasks(@PathVariable UUID bookingId,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(service.setupTasks(bookingId, actor, channel).stream()
                .map(task -> BookingResponses.SetupTaskResponse.from(task, clock))
                .toList());
    }

    @PostMapping("/{bookingId}/setup-tasks")
    @Operation(summary = "Raise turnaround work for a booking",
            description = "Deliberately not an S153 work order: a twenty-minute room turnaround does "
                    + "not belong in the same queue as a failed generator.")
    public ApiResponse<List<BookingResponses.SetupTaskResponse>> createSetupTasks(
            @PathVariable UUID bookingId, @Valid @RequestBody BookingRequests.CreateSetupTasks request,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(setup.create(new BookingCommands.CreateSetupTasks(bookingId,
                        request.tasks().stream()
                                .map(task -> new BookingCommands.CreateSetupTasks.NewSetupTask(
                                        task.description(), task.dueBy(), task.assignedTo()))
                                .toList(),
                        actor, channel)).stream()
                .map(task -> BookingResponses.SetupTaskResponse.from(task, clock))
                .toList());
    }

    // ---- internals ----------------------------------------------------------------------------

    private ApiResponse<BookingResponses.BookingResponse> transition(UUID bookingId,
            BookingCommands.TransitionBooking.Transition transition,
            BookingRequests.TransitionBooking request, ActorContext actor, SourceChannel channel) {
        return respond(service.transition(new BookingCommands.TransitionBooking(bookingId, transition,
                request.notes(), request.expectedVersion(), actor, channel)));
    }

    private ApiResponse<BookingResponses.BookingResponse> respond(Booking booking) {
        return ApiResponse.ok(BookingResponses.BookingResponse.from(booking, clock));
    }
}
