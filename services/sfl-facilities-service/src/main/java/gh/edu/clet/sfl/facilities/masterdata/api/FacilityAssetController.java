package gh.edu.clet.sfl.facilities.masterdata.api;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.masterdata.api.FacilitiesResponses.AssetResponse;
import gh.edu.clet.sfl.facilities.masterdata.application.FacilitiesCommands;
import gh.edu.clet.sfl.facilities.masterdata.application.FacilityAssetService;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.AssetCategory;
import gh.edu.clet.sfl.facilities.masterdata.domain.AssetCriticality;
import gh.edu.clet.sfl.facilities.masterdata.domain.AssetOperationalStatus;
import gh.edu.clet.sfl.facilities.shared.api.FacilitiesActorResolver;
import gh.edu.clet.sfl.facilities.shared.api.PageResponse;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
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
 * The facility asset register (SRS-SFL-S152-01, §21.1).
 *
 * <p>The record S153 will raise work orders against. Distinct from AVAMP-Lite's asset references,
 * which carry cross-programme identity; this is the fixed plant attached to a space that maintenance
 * is raised on.
 */
@RestController
@RequestMapping("/api/v1/facilities/assets")
@Tag(name = "S152 Facility assets", description = "Fixed plant and equipment: HVAC, lifts, generators, panels")
public class FacilityAssetController {

    private final FacilityAssetService service;
    private final FacilitiesActorResolver actorResolver;

    public FacilityAssetController(FacilityAssetService service, FacilitiesActorResolver actorResolver) {
        this.service = service;
        this.actorResolver = actorResolver;
    }

    @PostMapping
    @Operation(summary = "Register a facility asset", description = "Accepts an Idempotency-Key.")
    public ResponseEntity<AssetResponse> register(@Valid @RequestBody FacilitiesRequests.RegisterAsset request,
            HttpServletRequest http) {
        AssetResponse result = AssetResponse.from(service.register(new FacilitiesCommands.RegisterAsset(
                request.siteCode(), request.assetCode(), request.name(), request.category(),
                request.criticality(), request.roomId(), request.locationCode(), request.manufacturer(),
                request.modelNumber(), request.serialNumber(), request.installedOn(),
                request.warrantyExpiresOn(), request.serviceIntervalDays(), request.custodian(),
                request.deviceReferenceId(), request.assetReferenceId(), actor(http), channel(http),
                idempotencyKey(http))));
        return ResponseEntity.created(URI.create("/api/v1/facilities/assets/" + result.id())).body(result);
    }

    @GetMapping
    @Operation(summary = "Search facility assets by site, space, category, criticality and status")
    public PageResponse<AssetResponse> search(
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) UUID roomId,
            @RequestParam(required = false) AssetCategory category,
            @RequestParam(required = false) AssetCriticality criticality,
            @RequestParam(required = false) AssetOperationalStatus operationalStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest http) {
        FacilitiesRepository.AssetQuery query = new FacilitiesRepository.AssetQuery(siteCode, roomId, category,
                criticality, operationalStatus, page, size);
        return PageResponse.from(service.search(query, actor(http), channel(http)), AssetResponse::from);
    }

    @GetMapping("/{assetId}")
    @Operation(summary = "Read one facility asset")
    public AssetResponse find(@PathVariable UUID assetId, HttpServletRequest http) {
        return AssetResponse.from(service.find(assetId, actor(http), channel(http)));
    }

    @PatchMapping("/{assetId}")
    @Operation(summary = "Update a facility asset's attributes")
    public AssetResponse update(@PathVariable UUID assetId,
            @Valid @RequestBody FacilitiesRequests.UpdateAsset request, HttpServletRequest http) {
        return AssetResponse.from(service.update(new FacilitiesCommands.UpdateAsset(assetId, request.name(),
                request.category(), request.criticality(), request.manufacturer(), request.modelNumber(),
                request.serialNumber(), request.warrantyExpiresOn(), request.serviceIntervalDays(),
                request.custodian(), request.expectedVersion(), actor(http), channel(http))));
    }

    @PatchMapping("/{assetId}/status")
    @Operation(summary = "Change a facility asset's operational status",
            description = "Recomputes the readiness of the space the asset sits in. An impaired asset raises "
                    + "a blocker at a severity derived from its criticality; a recovered one resolves it.")
    public AssetResponse changeStatus(@PathVariable UUID assetId,
            @Valid @RequestBody FacilitiesRequests.ChangeAssetStatus request, HttpServletRequest http) {
        return AssetResponse.from(service.changeStatus(new FacilitiesCommands.ChangeAssetStatus(assetId,
                request.operationalStatus(), request.notes(), request.expectedVersion(), actor(http),
                channel(http))));
    }

    @PatchMapping("/{assetId}/location")
    @Operation(summary = "Move a facility asset to another space",
            description = "Recomputes readiness for both the space it left and the space it joined.")
    public AssetResponse relocate(@PathVariable UUID assetId,
            @Valid @RequestBody FacilitiesRequests.RelocateAsset request, HttpServletRequest http) {
        return AssetResponse.from(service.relocate(new FacilitiesCommands.RelocateAsset(assetId,
                request.roomId(), request.locationCode(), request.expectedVersion(), actor(http),
                channel(http))));
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
