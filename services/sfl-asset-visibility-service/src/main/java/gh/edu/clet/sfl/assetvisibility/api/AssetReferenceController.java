package gh.edu.clet.sfl.assetvisibility.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import gh.edu.clet.sfl.assetvisibility.application.AssetVisibilityService;
import gh.edu.clet.sfl.assetvisibility.application.AssignCustodyCommand;
import gh.edu.clet.sfl.assetvisibility.application.LinkEvidenceCommand;
import gh.edu.clet.sfl.assetvisibility.application.MoveAssetCommand;
import gh.edu.clet.sfl.assetvisibility.application.RegisterAssetCommand;
import gh.edu.clet.sfl.assetvisibility.domain.AssetCategory;
import gh.edu.clet.sfl.assetvisibility.domain.AssetReference;
import gh.edu.clet.sfl.assetvisibility.domain.LocationType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetReferenceController {

    private final AssetVisibilityService service;

    private final AssetVisibilityActorResolver actors;

    public AssetReferenceController(AssetVisibilityService service, AssetVisibilityActorResolver actors) {
        this.service = service;
        this.actors = actors;
    }

    @PostMapping
    public ResponseEntity<AssetReference> register(@Valid @RequestBody RegisterAssetRequest request,
            HttpServletRequest http) {
        String actor = actors.resolveActor(http);
        String correlationId = actors.resolveCorrelationId(http);
        AssetReference result = service.register(new RegisterAssetCommand(request.assetCode(), request.name(),
                request.category(), request.siteCode(), request.locationType(), request.locationReference(),
                request.custodianReference(), request.externalReference(), actor, correlationId));
        return ResponseEntity.created(URI.create("/api/v1/assets/" + result.id())).body(result);
    }

    @GetMapping
    public List<AssetReference> assets(@RequestParam(required = false) String siteCode) {
        return service.findAll(siteCode);
    }

    @GetMapping("/{assetId}")
    public AssetReference asset(@PathVariable UUID assetId) {
        return service.findById(assetId);
    }

    @GetMapping("/by-location")
    public List<AssetReference> byLocation(@RequestParam String siteCode, @RequestParam LocationType locationType,
            @RequestParam String locationReference) {
        return service.findByLocation(siteCode, locationType, locationReference);
    }

    @PatchMapping("/{assetId}/location")
    public AssetReference move(@PathVariable UUID assetId, @Valid @RequestBody MoveAssetRequest request,
            HttpServletRequest http) {
        String actor = actors.resolveActor(http);
        String correlationId = actors.resolveCorrelationId(http);
        return service.move(new MoveAssetCommand(assetId, request.locationType(), request.locationReference(), actor,
                correlationId));
    }

    @PatchMapping("/{assetId}/custody")
    public AssetReference assignCustody(@PathVariable UUID assetId, @Valid @RequestBody AssignCustodyRequest request,
            HttpServletRequest http) {
        String actor = actors.resolveActor(http);
        String correlationId = actors.resolveCorrelationId(http);
        return service.assignCustody(new AssignCustodyCommand(assetId, request.custodianReference(), actor,
                correlationId));
    }

    @PatchMapping("/{assetId}/evidence")
    public AssetReference linkEvidence(@PathVariable UUID assetId, @Valid @RequestBody LinkEvidenceRequest request,
            HttpServletRequest http) {
        String actor = actors.resolveActor(http);
        String correlationId = actors.resolveCorrelationId(http);
        return service.linkEvidence(new LinkEvidenceCommand(assetId, request.evidenceReference(), actor,
                correlationId));
    }

    public record RegisterAssetRequest(@NotBlank @Size(max = 80) String assetCode,
            @NotBlank @Size(max = 160) String name, @NotNull AssetCategory category,
            @NotBlank @Size(max = 40) String siteCode, @NotNull LocationType locationType,
            @NotBlank @Size(max = 120) String locationReference, @Size(max = 160) String custodianReference,
            @Size(max = 160) String externalReference) {
    }

    public record MoveAssetRequest(@NotNull LocationType locationType,
            @NotBlank @Size(max = 120) String locationReference) {
    }

    public record AssignCustodyRequest(@Size(max = 160) String custodianReference) {
    }

    public record LinkEvidenceRequest(@NotBlank @Size(max = 180) String evidenceReference) {
    }
}