package gh.edu.clet.sfl.facilities.booking.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.booking.application.BookingAvailabilityService;
import gh.edu.clet.sfl.facilities.booking.domain.BookingPurpose;
import gh.edu.clet.sfl.facilities.booking.domain.BookingWindow;
import gh.edu.clet.sfl.facilities.booking.domain.ResourceCategory;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.shared.api.FacilitiesActorResolver;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * "What is free?" — SRS-SFL-S159-02.
 *
 * <p>These endpoints reserve nothing. Two people can both be told Hall A is free and both request it;
 * the first wins and the second is refused. Holding a space during a five-minute browse would mean
 * the estate's diary was mostly locked by people who had wandered off.
 *
 * <p>{@code setupMinutes} and {@code teardownMinutes} are accepted here because availability must be
 * asked with the same buffers the booking will carry — a hall that looks free for a two-hour
 * examination is not free once thirty minutes of layout change are added at each end.
 */
@RestController
@RequestMapping("/api/v1/facilities/booking-availability")
@Tag(name = "S159 Availability", description = "What spaces and resources are free for a window")
public class BookingAvailabilityController {

    private final BookingAvailabilityService availability;
    private final FacilitiesActorResolver actorResolver;
    private final Clock clock;

    public BookingAvailabilityController(BookingAvailabilityService availability,
            FacilitiesActorResolver actorResolver, Clock clock) {
        this.availability = availability;
        this.actorResolver = actorResolver;
        this.clock = clock;
    }

    @GetMapping("/spaces")
    @Operation(summary = "Candidate spaces for a window, each with a verdict",
            description = "Unavailable spaces are returned with the reason rather than filtered out: "
                    + "'Hall A is blocked' is more use than Hall A being absent from a list.")
    public ApiResponse<List<BookingResponses.SpaceAvailabilityResponse>> spaces(
            @RequestParam String siteCode,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) BookingPurpose purpose,
            @RequestParam(required = false) SpaceType spaceType,
            @RequestParam(required = false) Integer minimumCapacity,
            @RequestParam(defaultValue = "0") int setupMinutes,
            @RequestParam(defaultValue = "0") int teardownMinutes,
            HttpServletRequest http) {
        BookingWindow window = new BookingWindow(from, to, setupMinutes, teardownMinutes);
        return ApiResponse.ok(availability.spaces(siteCode, window, purpose, spaceType, minimumCapacity,
                        actor(http), channel(http)).stream()
                .map(space -> BookingResponses.SpaceAvailabilityResponse.from(space, clock))
                .toList());
    }

    @GetMapping("/resources")
    @Operation(summary = "Bookable resources and how much of each is free for a window")
    public ApiResponse<List<BookingResponses.ResourceAvailabilityResponse>> resources(
            @RequestParam String siteCode,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) ResourceCategory category,
            @RequestParam(defaultValue = "0") int setupMinutes,
            @RequestParam(defaultValue = "0") int teardownMinutes,
            HttpServletRequest http) {
        BookingWindow window = new BookingWindow(from, to, setupMinutes, teardownMinutes);
        return ApiResponse.ok(availability.resources(siteCode, window, category, actor(http),
                        channel(http)).stream()
                .map(BookingResponses.ResourceAvailabilityResponse::from)
                .toList());
    }

    @GetMapping("/calendar")
    @Operation(summary = "Everything holding one space between two instants")
    public ApiResponse<List<BookingResponses.BookingResponse>> calendar(@RequestParam UUID roomId,
            @RequestParam Instant from, @RequestParam Instant to, HttpServletRequest http) {
        return ApiResponse.ok(availability.calendar(roomId, BookingWindow.of(from, to), actor(http),
                        channel(http)).stream()
                .map(booking -> BookingResponses.BookingResponse.from(booking, clock))
                .toList());
    }

    private ActorContext actor(HttpServletRequest http) {
        return actorResolver.resolve(http);
    }

    private SourceChannel channel(HttpServletRequest http) {
        return actorResolver.resolveSourceChannel(http);
    }
}
