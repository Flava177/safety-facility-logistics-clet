package gh.edu.clet.sfl.facilities.booking.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.booking.application.BookableResourceService;
import gh.edu.clet.sfl.facilities.booking.application.BookingCommands;
import gh.edu.clet.sfl.facilities.booking.domain.ResourceCategory;
import gh.edu.clet.sfl.facilities.shared.api.FacilitiesActorResolver;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The bookable-resource register — SRS-SFL-S159-01.
 *
 * <p>Separate from {@code /facility-assets} and deliberately so: an asset is fixed plant whose
 * condition feeds a space's readiness, a resource is portable and its scarcity is the point. The same
 * physical projector can move between the two registers over its life, which is why {@code assetId}
 * links them as a value rather than as a foreign key.
 */
@RestController
@RequestMapping("/api/v1/facilities/bookable-resources")
@Tag(name = "S159 Bookable resources", description = "Projectors, furniture sets and other movable resources")
public class BookableResourceController {

    private final BookableResourceService service;
    private final FacilitiesActorResolver actorResolver;

    public BookableResourceController(BookableResourceService service,
            FacilitiesActorResolver actorResolver) {
        this.service = service;
        this.actorResolver = actorResolver;
    }

    @PostMapping
    @Operation(summary = "Register a bookable resource",
            description = "One row for a set of forty chairs, not forty rows. A quantity of exactly "
                    + "one makes the resource exclusive, which is what lets the database refuse a "
                    + "second booking of it under concurrency.")
    public ResponseEntity<ApiResponse<BookingResponses.ResourceResponse>> register(
            @Valid @RequestBody BookingRequests.RegisterResource request, HttpServletRequest http) {
        BookingResponses.ResourceResponse result = BookingResponses.ResourceResponse.from(
                service.register(new BookingCommands.RegisterResource(request.siteCode(),
                        request.resourceCode(), request.name(), request.category(), request.description(),
                        request.quantity(), request.homeRoomId(), request.assetId(), request.requiresSetup(),
                        actor(http), channel(http), idempotencyKey(http), request)));
        return ResponseEntity.created(URI.create("/api/v1/facilities/bookable-resources/" + result.id()))
                .body(ApiResponse.ok(result));
    }

    @GetMapping
    @Operation(summary = "List bookable resources")
    public ApiResponse<List<BookingResponses.ResourceResponse>> search(
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) ResourceCategory category,
            HttpServletRequest http) {
        return ApiResponse.ok(service.search(siteCode, category, actor(http), channel(http)).stream()
                .map(BookingResponses.ResourceResponse::from)
                .toList());
    }

    @GetMapping("/{resourceId}")
    @Operation(summary = "Read one bookable resource")
    public ApiResponse<BookingResponses.ResourceResponse> findById(@PathVariable UUID resourceId,
            HttpServletRequest http) {
        return ApiResponse.ok(BookingResponses.ResourceResponse.from(
                service.findById(resourceId, actor(http), channel(http))));
    }

    @PatchMapping("/{resourceId}")
    @Operation(summary = "Update a bookable resource",
            description = "Reducing a quantity below what is already allocated is allowed: the chairs "
                    + "are genuinely gone, and the oversubscription surfaces on the availability "
                    + "screen where a human can decide which booking loses out.")
    public ApiResponse<BookingResponses.ResourceResponse> update(@PathVariable UUID resourceId,
            @Valid @RequestBody BookingRequests.UpdateResource request, HttpServletRequest http) {
        return ApiResponse.ok(BookingResponses.ResourceResponse.from(
                service.update(new BookingCommands.UpdateResource(resourceId, request.name(),
                        request.description(), request.quantity(), request.homeRoomId(),
                        request.requiresSetup(), request.expectedVersion(), actor(http), channel(http)))));
    }

    @PatchMapping("/{resourceId}/lifecycle")
    @Operation(summary = "Retire or restore a bookable resource")
    public ApiResponse<BookingResponses.ResourceResponse> changeLifecycle(@PathVariable UUID resourceId,
            @Valid @RequestBody BookingRequests.ChangeLifecycle request, HttpServletRequest http) {
        return ApiResponse.ok(BookingResponses.ResourceResponse.from(
                service.changeLifecycle(new BookingCommands.ChangeResourceLifecycle(resourceId,
                        request.lifecycleStatus(), request.expectedVersion(), actor(http),
                        channel(http)))));
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
