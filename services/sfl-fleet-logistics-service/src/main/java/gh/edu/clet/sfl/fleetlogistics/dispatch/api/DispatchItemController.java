package gh.edu.clet.sfl.fleetlogistics.dispatch.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.CourierItemService;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CourierItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditEvent;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.FleetActorResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** S171-01 Mailroom / Courier and Dispatch Tracking: courier item register and lifecycle. */
@RestController
@RequestMapping("/api/v1/dispatch/items")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Dispatch Items")
public class DispatchItemController {

    private final CourierItemService service;
    private final FleetActorResolver actors;

    public DispatchItemController(CourierItemService service, FleetActorResolver actors) {
        this.service = service;
        this.actors = actors;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CourierItem>> register(@Valid @RequestBody RegisterItemRequest r,
            HttpServletRequest h) {
        var item = service.registerItem(new CourierItemService.RegisterItem(r.siteCode(), r.itemNumber(),
                r.direction(), r.itemType(), r.sensitivity(), r.origin(), r.destination(), r.sender(), r.recipient(),
                r.assignedHandler(), actors.resolve(h), actors.resolveSourceChannel(h)));
        return ResponseEntity.created(URI.create("/api/v1/dispatch/items/" + item.id())).body(ApiResponse.ok(item));
    }

    @GetMapping
    public ApiResponse<DispatchPageResponse<CourierItem>> list(@RequestParam String siteCode,
            @RequestParam(required = false) CourierItem.Direction direction,
            @RequestParam(required = false) CourierItem.Status status,
            @RequestParam(required = false) CourierItem.Sensitivity sensitivity,
            @RequestParam(required = false) String handler,
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) UUID dispatchId,
            @RequestParam(required = false) Boolean undelivered,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String sort,
            HttpServletRequest h) {
        return ApiResponse.ok(DispatchPageResponse.of(service.items(siteCode, direction, status, sensitivity, handler,
                reference, dispatchId, undelivered, from, to, DispatchPageResponse.paging(page, size, sort),
                actors.resolve(h))));
    }

    /** The item's transition history: registration, every lifecycle move, misroute and closure. */
    @GetMapping("/{id}/history")
    public ApiResponse<List<AuditEvent>> history(@PathVariable UUID id, HttpServletRequest h) {
        return ApiResponse.ok(service.history(id, actors.resolve(h)));
    }

    @GetMapping("/{id}")
    public ApiResponse<CourierItem> detail(@PathVariable UUID id, HttpServletRequest h) {
        return ApiResponse.ok(service.item(id, actors.resolve(h)));
    }

    @PostMapping("/{id}/{action:stage|dispatch|in-transit|deliver|return|close}")
    public ApiResponse<CourierItem> advance(@PathVariable UUID id, @PathVariable String action, HttpServletRequest h) {
        return ApiResponse.ok(service.advanceItem(id, action, actors.resolve(h), actors.resolveSourceChannel(h)));
    }

    @PostMapping("/{id}/misroute")
    public ApiResponse<CourierItem> misroute(@PathVariable UUID id, @Valid @RequestBody MisrouteRequest r,
            HttpServletRequest h) {
        return ApiResponse.ok(service.misrouteItem(id, r.reason(), r.handler(), actors.resolve(h),
                actors.resolveSourceChannel(h)));
    }

    public record RegisterItemRequest(@NotBlank String siteCode, String itemNumber,
            @NotNull CourierItem.Direction direction, @NotNull CourierItem.Type itemType,
            @NotNull CourierItem.Sensitivity sensitivity, @NotBlank String origin, @NotBlank String destination,
            String sender, String recipient, String assignedHandler) {}

    public record MisrouteRequest(@NotBlank String reason, String handler) {}
}
