package gh.edu.clet.sfl.fleetlogistics.fleet.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.mapper.FleetEvidenceMapper;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.request.FleetEvidenceRequests;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.FleetEvidenceResponses.EvidenceResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.FleetEvidenceResponses.ExportRequestResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.EvidenceCommands;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetEvidenceApplicationService;
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

/** Evidence metadata, access and export endpoints (SRS-SFL-S166-03). */
@RestController
@RequestMapping("/api/v1/fleet/evidence")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Evidence")
class FleetEvidenceController {

    private final FleetEvidenceApplicationService evidenceService;
    private final FleetEvidenceMapper mapper;
    private final FleetActorResolver actorResolver;

    FleetEvidenceController(FleetEvidenceApplicationService evidenceService, FleetEvidenceMapper mapper,
            FleetActorResolver actorResolver) {
        this.evidenceService = evidenceService;
        this.mapper = mapper;
        this.actorResolver = actorResolver;
    }

    @PostMapping
    ResponseEntity<ApiResponse<EvidenceResponse>> register(
            @Valid @RequestBody FleetEvidenceRequests.RegisterEvidence request, HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        var evidence = evidenceService.register(new EvidenceCommands.RegisterEvidence(request.siteCode(),
                request.relatedRecordType(), request.relatedRecordId(), request.evidenceType(),
                request.fileName(), request.contentType(), request.storageReference(), request.sha256Hash(),
                request.retentionClass(), request.retentionExpiresAt(), actor,
                actorResolver.resolveSourceChannel(httpRequest)));
        return ResponseEntity.created(URI.create("/api/v1/fleet/evidence/" + evidence.id()))
                .body(ApiResponse.ok(mapper.toResponse(evidence)));
    }

    /**
      * Evidence attached to one record.
      *
      * <p>Closes gap 5, which the register called the main usability cost in the whole dashboard: with
      * no search, every closure dialog asked an operator to paste a reference id from somewhere else.
      * A trip or workflow closure can offer a picker now.
      */
    @GetMapping
    public ApiResponse<List<EvidenceResponse>> search(@RequestParam String relatedRecordType,
            @RequestParam String relatedRecordId, HttpServletRequest httpRequest) {
        return ApiResponse.ok(evidenceService
                .findByRelatedRecord(relatedRecordType, relatedRecordId, actorResolver.resolve(httpRequest)).stream()
                .map(mapper::toResponse)
                .toList());
    }

    @GetMapping("/{evidenceId}")
    ApiResponse<EvidenceResponse> findById(@PathVariable UUID evidenceId, HttpServletRequest httpRequest) {
        return ApiResponse.ok(mapper.toResponse(evidenceService.findById(evidenceId,
                actorResolver.resolve(httpRequest))));
    }

    @PostMapping("/{evidenceId}/access")
    ApiResponse<EvidenceResponse> recordAccess(@PathVariable UUID evidenceId, HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        return ApiResponse.ok(mapper.toResponse(evidenceService.recordAccess(evidenceId, actor,
                actorResolver.resolveSourceChannel(httpRequest))));
    }

    @PostMapping("/{evidenceId}/export-requests")
    ResponseEntity<ApiResponse<ExportRequestResponse>> requestExport(@PathVariable UUID evidenceId,
            @Valid @RequestBody FleetEvidenceRequests.RequestExport request, HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        var exportRequest = evidenceService.requestExport(new EvidenceCommands.RequestEvidenceExport(evidenceId,
                request.reason(), actor, actorResolver.resolveSourceChannel(httpRequest)));
        return ResponseEntity.created(URI.create("/api/v1/fleet/evidence/export-requests/"
                        + exportRequest.id()))
                .body(ApiResponse.ok(mapper.toResponse(exportRequest)));
    }

    @PatchMapping("/export-requests/{exportRequestId}/decision")
    ApiResponse<ExportRequestResponse> decideExport(@PathVariable UUID exportRequestId,
            @Valid @RequestBody FleetEvidenceRequests.DecideExport request, HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        return ApiResponse.ok(mapper.toResponse(evidenceService.decideExport(
                new EvidenceCommands.DecideEvidenceExport(exportRequestId, request.approved(),
                        request.decisionReason(), actor, actorResolver.resolveSourceChannel(httpRequest)))));
    }

    @PostMapping("/export-requests/{exportRequestId}/export")
    ApiResponse<ExportRequestResponse> export(@PathVariable UUID exportRequestId, HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        return ApiResponse.ok(mapper.toResponse(evidenceService.export(new EvidenceCommands.ExportEvidence(
                exportRequestId, actor, actorResolver.resolveSourceChannel(httpRequest)))));
    }
}
