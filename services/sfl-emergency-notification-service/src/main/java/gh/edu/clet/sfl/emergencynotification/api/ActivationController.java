package gh.edu.clet.sfl.emergencynotification.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.emergencynotification.application.service.ActivationService;
import gh.edu.clet.sfl.emergencynotification.domain.model.ChannelType;
import gh.edu.clet.sfl.emergencynotification.domain.model.NotificationActivation;
import gh.edu.clet.sfl.emergencynotification.domain.model.Priority;
import gh.edu.clet.sfl.emergencynotification.domain.model.RetentionClass;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** SRS-SFL-S174-02: emergency notification activation workflow. */
@RestController
@RequestMapping("/api/v1/emergency/activations")
@Tag(name = "Activations")
public class ActivationController {

    private final ActivationService service;
    private final EmergencyActorResolver actors;

    public ActivationController(ActivationService service, EmergencyActorResolver actors) {
        this.service = service;
        this.actors = actors;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NotificationActivation>> create(@Valid @RequestBody CreateRequest r,
            HttpServletRequest h) {
        var a = service.createDraft(command(r, h));
        return ResponseEntity.created(URI.create("/api/v1/emergency/activations/" + a.id())).body(ApiResponse.ok(a));
    }

    @GetMapping
    public ApiResponse<List<NotificationActivation>> list(@RequestParam String siteCode,
            @RequestParam(required = false) NotificationActivation.Status status, HttpServletRequest h) {
        return ApiResponse.ok(service.list(siteCode, status, actors.resolve(h)));
    }

    @GetMapping("/{id}")
    public ApiResponse<NotificationActivation> detail(@PathVariable UUID id, HttpServletRequest h) {
        return ApiResponse.ok(service.get(id, actors.resolve(h)));
    }

    @GetMapping("/{id}/status")
    public ApiResponse<ActivationService.ActivationStatusView> status(@PathVariable UUID id, HttpServletRequest h) {
        return ApiResponse.ok(service.status(id, actors.resolve(h)));
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<NotificationActivation> submit(@PathVariable UUID id, HttpServletRequest h) {
        return ApiResponse.ok(service.submit(id, actors.resolve(h), actors.resolveSourceChannel(h)));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<NotificationActivation> approve(@PathVariable UUID id, HttpServletRequest h) {
        return ApiResponse.ok(service.approve(id, actors.resolve(h), actors.resolveSourceChannel(h)));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<NotificationActivation> reject(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r,
            HttpServletRequest h) {
        return ApiResponse.ok(service.reject(id, r.reason(), actors.resolve(h), actors.resolveSourceChannel(h)));
    }

    @PostMapping("/{id}/activate")
    public ApiResponse<NotificationActivation> activate(@PathVariable UUID id, HttpServletRequest h) {
        return ApiResponse.ok(service.activate(id, actors.resolve(h), actors.resolveSourceChannel(h)));
    }

    @PostMapping("/{id}/after-action-approval")
    public ApiResponse<NotificationActivation> afterAction(@PathVariable UUID id,
            @Valid @RequestBody JustificationRequest r, HttpServletRequest h) {
        return ApiResponse.ok(service.afterActionApprove(id, r.justification(), actors.resolve(h),
                actors.resolveSourceChannel(h)));
    }

    @PostMapping("/{id}/all-clear")
    public ApiResponse<NotificationActivation> allClear(@PathVariable UUID id, HttpServletRequest h) {
        return ApiResponse.ok(service.allClear(id, actors.resolve(h), actors.resolveSourceChannel(h)));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<NotificationActivation> close(@PathVariable UUID id, @Valid @RequestBody CloseRequest r,
            HttpServletRequest h) {
        var evidence = new ActivationService.EvidenceMeta(r.evidenceFileName(), r.evidenceContentType(),
                r.evidenceStorageReference(), r.evidenceSha256(),
                r.retentionClass() == null ? RetentionClass.INCIDENT_10_YEARS : r.retentionClass());
        return ApiResponse.ok(service.close(id, r.reason(), evidence, actors.resolve(h),
                actors.resolveSourceChannel(h)));
    }

    private ActivationService.CreateActivation command(CreateRequest r, HttpServletRequest h) {
        return new ActivationService.CreateActivation(r.siteCode(), r.scenarioId(), r.templateId(),
                r.audienceGroupIds() == null ? List.of() : r.audienceGroupIds(),
                r.recipientZoneIds() == null ? List.of() : r.recipientZoneIds(),
                r.channels() == null ? List.of() : r.channels(), r.priority(), r.incidentReference(),
                actors.resolveIdempotencyKey(h), actors.resolve(h), actors.resolveSourceChannel(h));
    }

    public record CreateRequest(@NotBlank String siteCode, UUID scenarioId, UUID templateId,
            List<UUID> audienceGroupIds, List<UUID> recipientZoneIds, List<ChannelType> channels, Priority priority,
            String incidentReference) {}

    public record ReasonRequest(@NotBlank String reason) {}

    public record JustificationRequest(@NotBlank String justification) {}

    public record CloseRequest(@NotBlank String reason, String evidenceFileName, String evidenceContentType,
            @NotBlank String evidenceStorageReference, String evidenceSha256, RetentionClass retentionClass) {}
}
