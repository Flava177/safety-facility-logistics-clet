package gh.edu.clet.sfl.emergencynotification.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.emergencynotification.application.service.ActivationService;
import gh.edu.clet.sfl.emergencynotification.domain.model.ChannelType;
import gh.edu.clet.sfl.emergencynotification.domain.model.NotificationActivation;
import gh.edu.clet.sfl.emergencynotification.application.port.EmergencyRepository;
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
    public ApiResponse<EmergencyPageResponse<NotificationActivation>> list(@RequestParam String siteCode,
            @RequestParam(required = false) NotificationActivation.Status status,
            @RequestParam(required = false) NotificationActivation.Mode mode,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) String incidentReference,
            @RequestParam(required = false) Boolean openOnly,
            @RequestParam(required = false) Boolean liveOnly,
            @RequestParam(required = false) Boolean afterActionOutstanding,
            @RequestParam(required = false) UUID scenarioId,
            @RequestParam(required = false) UUID templateId,
            @RequestParam(required = false) java.time.Instant from,
            @RequestParam(required = false) java.time.Instant to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String sort,
            HttpServletRequest h) {
        return ApiResponse.ok(EmergencyPageResponse.of(service.list(siteCode, status, mode, priority,
                incidentReference, openOnly, liveOnly, afterActionOutstanding, scenarioId, templateId, from, to,
                EmergencyPageResponse.paging(page, size, sort), actors.resolve(h))));
    }

    /**
     * The activation's recorded transitions, oldest first.
     *
     * <p>Written on every state change since the service was built and readable by nothing until
     * now, which is why the detail screen used to reconstruct a timeline from the record's own
     * fields and silently omit any transition that left none behind.
     */
    @GetMapping("/{id}/history")
    public ApiResponse<List<EmergencyRepository.ActivationHistoryEntry>> history(@PathVariable UUID id,
            HttpServletRequest h) {
        return ApiResponse.ok(service.history(id, actors.resolve(h)));
    }

    /** Per-recipient delivery receipts and acknowledgements for this activation. */
    @GetMapping("/{id}/delivery")
    public ApiResponse<ActivationService.DeliveryDetail> delivery(@PathVariable UUID id, HttpServletRequest h) {
        return ApiResponse.ok(service.deliveryDetail(id, actors.resolve(h)));
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

    @PostMapping("/{id}/cancel")
    public ApiResponse<NotificationActivation> cancel(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r,
            HttpServletRequest h) {
        return ApiResponse.ok(service.cancel(id, r.reason(), actors.resolve(h), actors.resolveSourceChannel(h)));
    }

    @PostMapping("/{id}/activate")
    public ApiResponse<NotificationActivation> activate(@PathVariable UUID id, HttpServletRequest h) {
        return ApiResponse.ok(service.activate(id, actors.resolve(h), actors.resolveSourceChannel(h)));
    }

    @PostMapping("/{id}/degraded-fallback")
    public ApiResponse<NotificationActivation> degradedFallback(@PathVariable UUID id,
            @Valid @RequestBody FallbackRequest r, HttpServletRequest h) {
        return ApiResponse.ok(service.degradedFallback(id, r.fallbackPath(), actors.resolve(h),
                actors.resolveSourceChannel(h)));
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

    @PostMapping("/{id}/reopen")
    public ApiResponse<NotificationActivation> reopen(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r,
            HttpServletRequest h) {
        return ApiResponse.ok(service.reopen(id, r.reason(), actors.resolve(h), actors.resolveSourceChannel(h)));
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

    public record FallbackRequest(@NotBlank String fallbackPath) {}

    public record JustificationRequest(@NotBlank String justification) {}

    public record CloseRequest(@NotBlank String reason, String evidenceFileName, String evidenceContentType,
            @NotBlank String evidenceStorageReference, String evidenceSha256, RetentionClass retentionClass) {}
}
