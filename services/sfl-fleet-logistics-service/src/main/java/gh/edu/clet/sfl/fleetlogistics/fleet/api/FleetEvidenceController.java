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
        // Metadata-only registration by definition has no bytes behind it.
        return ResponseEntity.created(URI.create("/api/v1/fleet/evidence/" + evidence.id()))
                .body(ApiResponse.ok(mapper.toResponse(evidence, false)));
    }

    /**
     * Upload a file and register it as evidence in one request.
     *
     * <p>Multipart rather than base64 in the JSON body: a 10 MB photograph becomes 13 MB of base64 and
     * has to be held in memory as a string before it is decoded, whereas multipart streams and the
     * container enforces its own size limit before a byte reaches this method.
     *
     * <p>The response is the ordinary evidence envelope. Nothing about the endpoint being multipart
     * changes what the caller gets back, which is what lets a form upload a receipt and use the
     * returned id in the same submission instead of asking a person to copy one.
     */
    @PostMapping(path = "/files", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ApiResponse<EvidenceResponse>> upload(
            @RequestParam String siteCode,
            @RequestParam String relatedRecordType,
            @RequestParam String relatedRecordId,
            @RequestParam String evidenceType,
            @RequestParam gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceRetentionClass retentionClass,
            @RequestParam(required = false) java.time.Instant retentionExpiresAt,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            HttpServletRequest httpRequest) throws java.io.IOException {
        ActorContext actor = actorResolver.resolve(httpRequest);
        var evidence = evidenceService.upload(new EvidenceCommands.UploadEvidence(siteCode, relatedRecordType,
                relatedRecordId, evidenceType, file.getOriginalFilename(), file.getContentType(),
                file.getBytes(), retentionClass, retentionExpiresAt, actor,
                actorResolver.resolveSourceChannel(httpRequest)));
        return ResponseEntity.created(URI.create("/api/v1/fleet/evidence/" + evidence.id()))
                .body(ApiResponse.ok(mapper.toResponse(evidence, true)));
    }

    /**
     * The stored file, as a download or as an inline preview.
     *
     * <p>One endpoint for both, because they differ only in {@code Content-Disposition} and there is no
     * second set of rules worth maintaining. {@code ?disposition=inline} is what a preview pane asks
     * for; the default is a download.
     *
     * <h2>The headers are the security control</h2>
     *
     * <p>Serving a user-supplied file back from the application's own origin is how stored XSS
     * happens, so:
     *
     * <ul>
     *   <li>{@code X-Content-Type-Options: nosniff} stops a browser from deciding for itself that a
     *       JPEG is really HTML. Without it, content sniffing undoes the entire upload scan.</li>
     *   <li>{@code Content-Security-Policy: default-src 'none'; sandbox} means that even if something
     *       scriptable did get stored, it executes in an opaque origin with no network and no access
     *       to anything of the dashboard's.</li>
     *   <li>The content type is the one the <em>scanner</em> determined, never the one the uploader
     *       claimed, and it can only ever be {@code application/pdf} or {@code image/jpeg}.</li>
     *   <li>The file name is quoted and re-encoded per RFC 6266 rather than interpolated, because a
     *       file name is attacker-controlled text going into a response header.</li>
     * </ul>
     */
    @GetMapping("/{evidenceId}/content")
    ResponseEntity<byte[]> content(@PathVariable UUID evidenceId,
            @RequestParam(defaultValue = "attachment") String disposition, HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        var evidence = evidenceService.findById(evidenceId, actor);
        var file = evidenceService.content(evidenceId, actor, actorResolver.resolveSourceChannel(httpRequest));
        boolean inline = "inline".equalsIgnoreCase(disposition);

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, file.contentType())
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        org.springframework.http.ContentDisposition
                                .builder(inline ? "inline" : "attachment")
                                .filename(evidence.fileName(), java.nio.charset.StandardCharsets.UTF_8)
                                .build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                // Evidence is not something to leave in a shared cache or a proxy.
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(file.content());
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
        var found = evidenceService.findByRelatedRecord(relatedRecordType, relatedRecordId,
                actorResolver.resolve(httpRequest));
        var stored = evidenceService.withContent(found);
        return ApiResponse.ok(found.stream()
                .map(evidence -> mapper.toResponse(evidence, stored.contains(evidence.id())))
                .toList());
    }

    @GetMapping("/{evidenceId}")
    ApiResponse<EvidenceResponse> findById(@PathVariable UUID evidenceId, HttpServletRequest httpRequest) {
        return ApiResponse.ok(mapper.toResponse(evidenceService.findById(evidenceId,
                actorResolver.resolve(httpRequest)), evidenceService.hasContent(evidenceId)));
    }

    @PostMapping("/{evidenceId}/access")
    ApiResponse<EvidenceResponse> recordAccess(@PathVariable UUID evidenceId, HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        return ApiResponse.ok(mapper.toResponse(evidenceService.recordAccess(evidenceId, actor,
                actorResolver.resolveSourceChannel(httpRequest)), evidenceService.hasContent(evidenceId)));
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
