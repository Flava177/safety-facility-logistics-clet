package gh.edu.clet.sfl.fleetlogistics.dispatch.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.CourierItemService;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchEvidenceSupport.EvidenceMeta;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CourierItem;
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

/** S171-05 Inbound mail registration and internal distribution with a recorded acknowledgement. */
@RestController
@RequestMapping("/api/v1/dispatch/inbound")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Inbound Mail")
public class InboundMailController {

    private final CourierItemService service;
    private final FleetActorResolver actors;

    public InboundMailController(CourierItemService service, FleetActorResolver actors) {
        this.service = service;
        this.actors = actors;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CourierItem>> register(@Valid @RequestBody RegisterInboundRequest r,
            HttpServletRequest h) {
        var item = service.registerItem(new CourierItemService.RegisterItem(r.siteCode(), r.itemNumber(),
                CourierItem.Direction.INBOUND, r.itemType(), r.sensitivity(), r.origin(), r.destination(), r.sender(),
                r.recipient(), r.assignedHandler(), actors.resolve(h), actors.resolveSourceChannel(h)));
        return ResponseEntity.created(URI.create("/api/v1/dispatch/inbound/" + item.id())).body(ApiResponse.ok(item));
    }

    @GetMapping
    public ApiResponse<List<CourierItem>> list(@RequestParam String siteCode,
            @RequestParam(required = false) CourierItem.Status status,
            @RequestParam(required = false) String handler, @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to, @RequestParam(defaultValue = "100") int size,
            HttpServletRequest h) {
        return ApiResponse.ok(service.items(siteCode, CourierItem.Direction.INBOUND, status, null, handler, from, to,
                size, actors.resolve(h)));
    }

    @PostMapping("/{id}/distribute")
    public ApiResponse<CourierItem> distribute(@PathVariable UUID id, @Valid @RequestBody DistributeRequest r,
            HttpServletRequest h) {
        EvidenceMeta evidence = r.signatureStorageReference() == null || r.signatureStorageReference().isBlank() ? null
                : new EvidenceMeta(r.signatureFileName(), r.signatureContentType(), r.signatureStorageReference(),
                        r.signatureSha256(), r.retentionClass(), null);
        return ApiResponse.ok(service.distributeInbound(new CourierItemService.DistributeInbound(id, r.acknowledgedBy(),
                r.distributionReference(), evidence, actors.resolve(h), actors.resolveSourceChannel(h))));
    }

    public record RegisterInboundRequest(@NotBlank String siteCode, String itemNumber,
            @NotNull CourierItem.Type itemType, @NotNull CourierItem.Sensitivity sensitivity, @NotBlank String origin,
            @NotBlank String destination, String sender, String recipient, String assignedHandler) {}

    public record DistributeRequest(@NotBlank String acknowledgedBy, String distributionReference,
            String signatureFileName, String signatureContentType, String signatureStorageReference,
            String signatureSha256, String retentionClass) {}
}
