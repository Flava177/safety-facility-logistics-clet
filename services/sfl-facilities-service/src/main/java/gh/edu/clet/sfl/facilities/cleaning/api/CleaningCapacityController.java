package gh.edu.clet.sfl.facilities.cleaning.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.cleaning.application.CleaningCapacityService;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The read side of S169's capacity feed to S173 - SRS-SFL-S169-04.
 *
 * <p>The write side - {@code reserve}/{@code find}/{@code release} - is not REST: S173 and S169 share a
 * deployable in this build, so it calls the {@code EventCleaningCapacity} contract in-process, the same
 * way any two modules in one service call each other. This endpoint is the human-facing view of the
 * same data, for a facilities officer or S173 coordinator checking what is committed before they ask.
 */
@RestController
@RequestMapping("/api/v1/facilities/cleaning/capacity")
@Tag(name = "S169 Cleaning Capacity", description = "Schedule and available capacity, as offered to S173")
public class CleaningCapacityController {

    private final CleaningCapacityService capacity;

    public CleaningCapacityController(CleaningCapacityService capacity) {
        this.capacity = capacity;
    }

    @GetMapping
    @Operation(summary = "Cleaning capacity and committed tasks at a site over a window",
            description = "Crews configured, peak concurrent commitments, and each commitment described the way "
                    + "a conflict would name it.")
    public ApiResponse<CleaningResponses.CapacityViewResponse> view(@RequestParam String siteCode,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(CleaningResponses.CapacityViewResponse.from(
                capacity.view(siteCode, from, to, actor, channel)));
    }
}
