package gh.edu.clet.sfl.facilities.booking.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.booking.application.BookingCommands;
import gh.edu.clet.sfl.facilities.booking.application.BookingSetupService;
import gh.edu.clet.sfl.facilities.shared.api.PageResponse;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The room-turnaround queue - SRS-SFL-S159-02.
 *
 * <p>Ordered by when the room is needed rather than when the task was raised. A task for this
 * afternoon matters more than one raised last week for next month, and a created-at ordering gets
 * that backwards every time.
 */
@RestController
@RequestMapping("/api/v1/facilities/setup-tasks")
@Tag(name = "S159 Setup tasks", description = "Room turnaround before a booking")
public class BookingSetupTaskController {

    private final BookingSetupService service;
    private final Clock clock;

    public BookingSetupTaskController(BookingSetupService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping
    @Operation(summary = "The turnaround queue",
            description = "Everything still to do before a room is needed, most urgent first. "
                    + "Defaults to the next two days.")
    public ApiResponse<PageResponse<BookingResponses.SetupTaskResponse>> queue(
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) Instant dueBefore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(PageResponse.from(
                service.queue(siteCode, dueBefore, page, size, actor, channel),
                task -> BookingResponses.SetupTaskResponse.from(task, clock)));
    }

    @PatchMapping("/{taskId}/resolution")
    @Operation(summary = "Mark a setup task done, or deliberately skipped",
            description = "Skipping requires a reason: a skipped task that says nothing cannot be told "
                    + "from one nobody got to.")
    public ApiResponse<BookingResponses.SetupTaskResponse> resolve(@PathVariable UUID taskId,
            @Valid @RequestBody BookingRequests.ResolveSetupTask request, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(BookingResponses.SetupTaskResponse.from(
                service.resolve(new BookingCommands.ResolveSetupTask(taskId, request.outcome(),
                        request.notes(), actor, channel)),
                clock));
    }
}
