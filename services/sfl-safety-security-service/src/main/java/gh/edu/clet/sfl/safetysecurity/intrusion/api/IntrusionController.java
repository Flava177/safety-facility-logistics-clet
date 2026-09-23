package gh.edu.clet.sfl.safetysecurity.intrusion.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.safetysecurity.intrusion.application.service.IntrusionAlarmService;
import gh.edu.clet.sfl.safetysecurity.intrusion.application.service.IntrusionDispatchService;
import gh.edu.clet.sfl.safetysecurity.intrusion.application.service.IntrusionZoneService;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.AlarmStatus;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.DisarmOverride;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.DispatchOutcome;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.IntrusionAlarm;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.IntrusionZone;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.ResponseDispatch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SRS-SFL-S162-02..05: the operational surface over the SOC alarm queue, zone/disarm administration
 * and armed-response coordination - everything that is not raw integration ingestion (see
 * {@code IntrusionIntegrationController}). One controller, following
 * {@code AccessControlController}'s precedent of grouping a module's sub-resources together.
 */
@RestController
@RequestMapping("/api/v1/intrusion")
@Tag(name = "S162 Intrusion Detection & Alarm Monitoring")
public class IntrusionController {

    private final IntrusionAlarmService alarms;
    private final IntrusionZoneService zones;
    private final IntrusionDispatchService dispatches;
    private final IntrusionActorResolver actors;

    public IntrusionController(IntrusionAlarmService alarms, IntrusionZoneService zones,
            IntrusionDispatchService dispatches, IntrusionActorResolver actors) {
        this.alarms = alarms;
        this.zones = zones;
        this.dispatches = dispatches;
        this.actors = actors;
    }

    // ---- SOC alarm queue (S162-02/03) ----

    @PatchMapping("/alarms/{id}/acknowledgement")
    @Operation(summary = "Acknowledge an alarm in the SOC queue")
    public ApiResponse<IntrusionAlarm> acknowledge(@PathVariable UUID id, HttpServletRequest http) {
        return ApiResponse.ok(alarms.acknowledge(id, actors.resolve(http)));
    }

    @PatchMapping("/alarms/{id}/resolution")
    @Operation(summary = "Resolve an alarm in the SOC queue")
    public ApiResponse<IntrusionAlarm> resolve(@PathVariable UUID id, HttpServletRequest http) {
        return ApiResponse.ok(alarms.resolve(id, actors.resolve(http)));
    }

    @PatchMapping("/alarms/{id}/evidence")
    @Operation(summary = "Link CCTV (S161) evidence to an acknowledged alarm by reference")
    public ApiResponse<IntrusionAlarm> linkEvidence(@PathVariable UUID id,
            @Valid @RequestBody LinkEvidenceRequest request, HttpServletRequest http) {
        return ApiResponse.ok(alarms.linkEvidence(id, request.evidenceReference(), actors.resolve(http)));
    }

    @PostMapping("/alarms/{id}/incident")
    @Operation(summary = "Escalate an acknowledged alarm into a security incident (S163)")
    public ApiResponse<IntrusionAlarm> linkIncident(@PathVariable UUID id, HttpServletRequest http) {
        return ApiResponse.ok(alarms.linkIncident(id, actors.resolve(http)));
    }

    @GetMapping("/alarms")
    @Operation(summary = "The SOC alarm queue for a site")
    public ApiResponse<List<IntrusionAlarm>> alarmQueue(@RequestParam String siteCode,
            @RequestParam(defaultValue = "RAISED") AlarmStatus status, HttpServletRequest http) {
        return ApiResponse.ok(alarms.queue(siteCode, status, actors.resolve(http)));
    }

    // ---- Zones and disarm (S162-04) ----

    @PostMapping("/zones")
    @Operation(summary = "Define a protected zone and its arming schedule")
    public ResponseEntity<ApiResponse<IntrusionZone>> defineZone(@Valid @RequestBody DefineZoneRequest request,
            HttpServletRequest http) {
        IntrusionZone zone = zones.define(new IntrusionZoneService.DefineZone(request.siteCode(),
                request.zoneCode(), request.name(), request.locationRef(), request.armSchedule(),
                request.protectedZone(), actors.resolve(http)));
        return ResponseEntity.created(URI.create("/api/v1/intrusion/zones/" + zone.id())).body(ApiResponse.ok(zone));
    }

    @PatchMapping("/zones/{zoneId}/arming-confirmation")
    @Operation(summary = "Record whether the panel confirmed the zone armed at its scheduled time")
    public ApiResponse<IntrusionZone> confirmArmed(@PathVariable UUID zoneId,
            @Valid @RequestBody ConfirmArmedRequest request, HttpServletRequest http) {
        return ApiResponse.ok(zones.confirmArmed(new IntrusionZoneService.ConfirmArmed(zoneId,
                request.panelConfirmed(), request.expectedVersion(), actors.resolve(http))));
    }

    @PatchMapping("/zones/{zoneId}/examination-mode")
    @Operation(summary = "Enter Examination Mode: enforce mandatory arming for the exam window")
    public ApiResponse<IntrusionZone> enterExaminationMode(@PathVariable UUID zoneId,
            @Valid @RequestBody EnterExaminationModeRequest request, HttpServletRequest http) {
        return ApiResponse.ok(zones.enterExaminationMode(new IntrusionZoneService.EnterExaminationMode(zoneId,
                request.until(), request.expectedVersion(), actors.resolve(http))));
    }

    @DeleteMapping("/zones/{zoneId}/examination-mode")
    @Operation(summary = "Exit Examination Mode")
    public ApiResponse<IntrusionZone> exitExaminationMode(@PathVariable UUID zoneId,
            @RequestParam(required = false) Long expectedVersion, HttpServletRequest http) {
        return ApiResponse.ok(zones.exitExaminationMode(zoneId, expectedVersion, actors.resolve(http)));
    }

    @GetMapping("/zones")
    @Operation(summary = "List a site's protected zones")
    public ApiResponse<List<IntrusionZone>> zonesForSite(@RequestParam String siteCode, HttpServletRequest http) {
        return ApiResponse.ok(zones.forSite(siteCode, actors.resolve(http)));
    }

    @PostMapping("/disarm-overrides")
    @Operation(summary = "Request a time-bound, authorised disarm of a protected zone")
    public ResponseEntity<ApiResponse<DisarmOverride>> requestDisarm(@Valid @RequestBody DisarmRequest request,
            HttpServletRequest http) {
        DisarmOverride override = zones.requestDisarm(new IntrusionZoneService.RequestDisarm(request.siteCode(),
                request.zoneCode(), request.reason(), request.approverId(), request.startsAt(), request.expiresAt(),
                actors.resolve(http)));
        return ResponseEntity.created(URI.create("/api/v1/intrusion/disarm-overrides/" + override.id()))
                .body(ApiResponse.ok(override));
    }

    @DeleteMapping("/disarm-overrides/{id}")
    @Operation(summary = "Revoke an active disarm before its window ends, re-arming the zone")
    public ApiResponse<DisarmOverride> revokeDisarm(@PathVariable UUID id, HttpServletRequest http) {
        return ApiResponse.ok(zones.revokeDisarm(id, actors.resolve(http)));
    }

    // ---- Armed-response coordination (S162-05) ----

    @PostMapping("/alarms/{alarmId}/dispatch")
    @Operation(summary = "Request armed response / monitoring-service dispatch for a confirmed alarm")
    public ResponseEntity<ApiResponse<ResponseDispatch>> requestDispatch(@PathVariable UUID alarmId,
            @Valid @RequestBody RequestDispatchRequest request, HttpServletRequest http) {
        ResponseDispatch dispatch = dispatches.requestDispatch(alarmId, request.monitoringService(),
                actors.resolve(http));
        return ResponseEntity.created(URI.create("/api/v1/intrusion/dispatches/" + dispatch.id()))
                .body(ApiResponse.ok(dispatch));
    }

    @PatchMapping("/dispatches/{id}/acknowledgement")
    @Operation(summary = "Record the monitoring service's acknowledgement of a dispatch request")
    public ApiResponse<ResponseDispatch> acknowledgeDispatch(@PathVariable UUID id, HttpServletRequest http) {
        return ApiResponse.ok(dispatches.acknowledge(id, actors.resolve(http)));
    }

    @PatchMapping("/dispatches/{id}/outcome")
    @Operation(summary = "Record armed response's arrival and outcome")
    public ApiResponse<ResponseDispatch> recordOutcome(@PathVariable UUID id,
            @Valid @RequestBody RecordOutcomeRequest request, HttpServletRequest http) {
        return ApiResponse.ok(dispatches.recordOutcome(id, request.outcome(), request.notes(), actors.resolve(http)));
    }

    @GetMapping("/alarms/{alarmId}/dispatch")
    @Operation(summary = "The dispatch record for an alarm")
    public ApiResponse<ResponseDispatch> dispatchForAlarm(@PathVariable UUID alarmId, HttpServletRequest http) {
        return ApiResponse.ok(dispatches.forAlarm(alarmId, actors.resolve(http)));
    }

    @GetMapping("/dispatches/false-alarm-count")
    @Operation(summary = "The false-alarm trend count for a site")
    public ApiResponse<Long> falseAlarmCount(@RequestParam String siteCode, HttpServletRequest http) {
        return ApiResponse.ok(dispatches.falseAlarmCount(siteCode, actors.resolve(http)));
    }

    public record LinkEvidenceRequest(@NotBlank String evidenceReference) {
    }

    public record DefineZoneRequest(@NotBlank String siteCode, @NotBlank String zoneCode, @NotBlank String name,
            String locationRef, @NotBlank String armSchedule, boolean protectedZone) {
    }

    public record ConfirmArmedRequest(boolean panelConfirmed, Long expectedVersion) {
    }

    public record EnterExaminationModeRequest(@NotNull Instant until, Long expectedVersion) {
    }

    public record DisarmRequest(@NotBlank String siteCode, @NotBlank String zoneCode, @NotBlank String reason,
            @NotBlank String approverId, @NotNull Instant startsAt, @NotNull Instant expiresAt) {
    }

    public record RequestDispatchRequest(@NotBlank String monitoringService) {
    }

    public record RecordOutcomeRequest(@NotNull DispatchOutcome outcome, String notes) {
    }
}
