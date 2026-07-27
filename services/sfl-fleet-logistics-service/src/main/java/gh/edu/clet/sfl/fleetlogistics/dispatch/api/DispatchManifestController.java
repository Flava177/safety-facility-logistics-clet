package gh.edu.clet.sfl.fleetlogistics.dispatch.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchManifestService;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.Dispatch;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchManifestItem;
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

/** S171-02 Dispatch manifest: create, add items, seal (seal IDs + counts), assign trip, dispatch, close. */
@RestController
@RequestMapping("/api/v1/dispatch/manifests")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Dispatch Manifests")
public class DispatchManifestController {

    private final DispatchManifestService service;
    private final FleetActorResolver actors;

    public DispatchManifestController(DispatchManifestService service, FleetActorResolver actors) {
        this.service = service;
        this.actors = actors;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Dispatch>> create(@Valid @RequestBody CreateManifestRequest r,
            HttpServletRequest h) {
        var dispatch = service.createManifest(new DispatchManifestService.CreateManifest(r.siteCode(),
                r.manifestNumber(), r.route(), r.assignedHandler(), r.destinationCentre(), r.examinationContext(),
                r.tripId(), r.vehicleId(), r.driverId(), actors.resolve(h), actors.resolveSourceChannel(h)));
        return ResponseEntity.created(URI.create("/api/v1/dispatch/manifests/" + dispatch.id()))
                .body(ApiResponse.ok(dispatch));
    }

    @GetMapping
    public ApiResponse<List<Dispatch>> list(@RequestParam String siteCode,
            @RequestParam(required = false) Dispatch.Status status,
            @RequestParam(required = false) String destinationCentre, @RequestParam(required = false) UUID tripId,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "100") int size, HttpServletRequest h) {
        return ApiResponse.ok(service.dispatches(siteCode, status, destinationCentre, tripId, from, to, size,
                actors.resolve(h)));
    }

    @GetMapping("/{id}")
    public ApiResponse<Dispatch> detail(@PathVariable UUID id, HttpServletRequest h) {
        return ApiResponse.ok(service.dispatch(id, actors.resolve(h)));
    }

    @GetMapping("/{id}/items")
    public ApiResponse<List<DispatchManifestItem>> items(@PathVariable UUID id, HttpServletRequest h) {
        return ApiResponse.ok(service.manifestItems(id, actors.resolve(h)));
    }

    @PostMapping("/{id}/items")
    public ApiResponse<DispatchManifestItem> addItem(@PathVariable UUID id, @Valid @RequestBody AddItemRequest r,
            HttpServletRequest h) {
        return ApiResponse.ok(service.addItem(new DispatchManifestService.AddManifestItem(id, r.courierItemId(),
                r.expectedSealId(), r.expectedQuantity(), actors.resolve(h), actors.resolveSourceChannel(h))));
    }

    @PostMapping("/{id}/seal")
    public ApiResponse<Dispatch> seal(@PathVariable UUID id, @Valid @RequestBody SealRequest r, HttpServletRequest h) {
        return ApiResponse.ok(service.seal(id, r.sealIds(), actors.resolve(h), actors.resolveSourceChannel(h)));
    }

    @PostMapping("/{id}/assign-trip")
    public ApiResponse<Dispatch> assignTrip(@PathVariable UUID id, @RequestBody AssignTripRequest r,
            HttpServletRequest h) {
        return ApiResponse.ok(service.assignTrip(id, r.tripId(), r.vehicleId(), r.driverId(), actors.resolve(h),
                actors.resolveSourceChannel(h)));
    }

    @PostMapping("/{id}/dispatch")
    public ApiResponse<Dispatch> dispatch(@PathVariable UUID id, HttpServletRequest h) {
        return ApiResponse.ok(service.dispatch(id, actors.resolve(h), actors.resolveSourceChannel(h)));
    }

    @PostMapping("/{id}/in-transit")
    public ApiResponse<Dispatch> inTransit(@PathVariable UUID id, HttpServletRequest h) {
        return ApiResponse.ok(service.inTransit(id, actors.resolve(h), actors.resolveSourceChannel(h)));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<Dispatch> close(@PathVariable UUID id, @Valid @RequestBody CloseRequest r, HttpServletRequest h) {
        return ApiResponse.ok(service.close(id, r.reason(), actors.resolve(h), actors.resolveSourceChannel(h)));
    }

    public record CreateManifestRequest(@NotBlank String siteCode, String manifestNumber, @NotBlank String route,
            @NotBlank String assignedHandler, String destinationCentre, String examinationContext, UUID tripId,
            UUID vehicleId, UUID driverId) {}

    public record AddItemRequest(@NotNull UUID courierItemId, String expectedSealId, int expectedQuantity) {}

    public record SealRequest(@NotNull List<String> sealIds) {}

    public record AssignTripRequest(UUID tripId, UUID vehicleId, UUID driverId) {}

    public record CloseRequest(@NotBlank String reason) {}
}
