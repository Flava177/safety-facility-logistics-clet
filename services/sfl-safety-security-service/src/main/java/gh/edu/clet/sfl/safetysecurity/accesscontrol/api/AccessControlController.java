package gh.edu.clet.sfl.safetysecurity.accesscontrol.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.application.service.AccessExceptionService;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.application.service.AccessOccupancyService;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.application.service.AccessOverrideService;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.application.service.AccessProvisioningService;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.application.service.AccessZoneAdminService;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessException;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessOverride;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessProvisioning;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessZone;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.ExceptionStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
 * SRS-SFL-S160a-02..06: the operational surface over provisioning, overrides, the SOC exception
 * queue, zone/door-group administration and occupancy - everything that is not raw integration
 * ingestion (see {@code AccessControlIntegrationController}). One controller, following {@code
 * SecurityIncidentController}'s precedent of grouping a module's several sub-resources together
 * rather than one controller per aggregate.
 */
@RestController
@RequestMapping("/api/v1/access-control")
@Tag(name = "S160a Physical Access Control")
public class AccessControlController {

    private final AccessProvisioningService provisioning;
    private final AccessOverrideService overrides;
    private final AccessExceptionService exceptions;
    private final AccessZoneAdminService zones;
    private final AccessOccupancyService occupancy;
    private final AccessControlActorResolver actors;

    public AccessControlController(AccessProvisioningService provisioning, AccessOverrideService overrides,
            AccessExceptionService exceptions, AccessZoneAdminService zones, AccessOccupancyService occupancy,
            AccessControlActorResolver actors) {
        this.provisioning = provisioning;
        this.overrides = overrides;
        this.exceptions = exceptions;
        this.zones = zones;
        this.occupancy = occupancy;
        this.actors = actors;
    }

    // ---- Zones (S160a-05) ----

    @PostMapping("/zones")
    @Operation(summary = "Define an access zone, its schedule and door groups")
    public ResponseEntity<ApiResponse<AccessZone>> defineZone(@Valid @RequestBody DefineZoneRequest request,
            HttpServletRequest http) {
        AccessZone zone = zones.define(new AccessZoneAdminService.DefineZone(request.siteCode(), request.zoneCode(),
                request.name(), request.locationRef(), request.schedule(), request.doorGroups(),
                actors.resolve(http)));
        return ResponseEntity.created(URI.create("/api/v1/access-control/zones/" + zone.id()))
                .body(ApiResponse.ok(zone));
    }

    @PatchMapping("/zones/{zoneId}")
    @Operation(summary = "Redefine a zone's schedule and door groups")
    public ApiResponse<AccessZone> redefineZone(@PathVariable UUID zoneId,
            @Valid @RequestBody RedefineZoneRequest request, HttpServletRequest http) {
        return ApiResponse.ok(zones.redefine(new AccessZoneAdminService.Redefine(zoneId, request.schedule(),
                request.doorGroups(), request.expectedVersion(), actors.resolve(http))));
    }

    @PatchMapping("/zones/{zoneId}/examination-mode")
    @Operation(summary = "Enter Examination Mode: tighten the schedule and lock the named door groups")
    public ApiResponse<AccessZone> enterExaminationMode(@PathVariable UUID zoneId,
            @Valid @RequestBody EnterExaminationModeRequest request, HttpServletRequest http) {
        return ApiResponse.ok(zones.enterExaminationMode(new AccessZoneAdminService.EnterExaminationMode(zoneId,
                request.tightenedSchedule(), request.lockDoorGroups(), request.until(), request.expectedVersion(),
                actors.resolve(http))));
    }

    @DeleteMapping("/zones/{zoneId}/examination-mode")
    @Operation(summary = "Exit Examination Mode and restore the normal schedule")
    public ApiResponse<AccessZone> exitExaminationMode(@PathVariable UUID zoneId,
            @Valid @RequestBody ExitExaminationModeRequest request, HttpServletRequest http) {
        return ApiResponse.ok(zones.exitExaminationMode(zoneId, request.normalSchedule(), request.expectedVersion(),
                actors.resolve(http)));
    }

    @GetMapping("/zones")
    @Operation(summary = "List a site's access zones")
    public ApiResponse<List<AccessZone>> zonesForSite(@RequestParam String siteCode, HttpServletRequest http) {
        return ApiResponse.ok(zones.forSite(siteCode, actors.resolve(http)));
    }

    // ---- Provisioning (S160a-02) ----

    @PostMapping("/provisioning")
    @Operation(summary = "Manually grant access", description = "The exception, not the norm - requires a "
            + "reason, an approver and an expiry (SRS-SFL-S160a-02).")
    public ResponseEntity<ApiResponse<AccessProvisioning>> grantManually(
            @Valid @RequestBody ManualGrantRequest request, HttpServletRequest http) {
        AccessProvisioning granted = provisioning.grantManually(new AccessProvisioningService.ManualGrant(
                request.siteCode(), request.personRef(), request.zoneCode(), request.reason(), request.approverId(),
                request.expiresAt(), actors.resolve(http)));
        return ResponseEntity.created(URI.create("/api/v1/access-control/provisioning/" + granted.id()))
                .body(ApiResponse.ok(granted));
    }

    @DeleteMapping("/provisioning/{id}")
    @Operation(summary = "Revoke an access entitlement")
    public ApiResponse<AccessProvisioning> revokeProvisioning(@PathVariable UUID id, HttpServletRequest http) {
        return ApiResponse.ok(provisioning.revoke(id, actors.resolve(http)));
    }

    @GetMapping("/provisioning")
    @Operation(summary = "List a person's access entitlements")
    public ApiResponse<List<AccessProvisioning>> provisioningForPerson(@RequestParam String siteCode,
            @RequestParam String personRef, HttpServletRequest http) {
        return ApiResponse.ok(provisioning.forPerson(siteCode, personRef, actors.resolve(http)));
    }

    // ---- Overrides (S160a-03) ----

    @PostMapping("/overrides")
    @Operation(summary = "Request a time-bound access override")
    public ResponseEntity<ApiResponse<AccessOverride>> requestOverride(@Valid @RequestBody OverrideRequest request,
            HttpServletRequest http) {
        AccessOverride override = overrides.request(new AccessOverrideService.RequestOverride(request.siteCode(),
                request.scopeRef(), request.reason(), request.approverId(), request.startsAt(), request.expiresAt(),
                actors.resolve(http)));
        return ResponseEntity.created(URI.create("/api/v1/access-control/overrides/" + override.id()))
                .body(ApiResponse.ok(override));
    }

    @PostMapping("/overrides/break-glass")
    @Operation(summary = "Raise a break-glass override during a declared emergency",
            description = "No approver at creation - approved after the fact (SRS-SFL-S160a-03).")
    public ResponseEntity<ApiResponse<AccessOverride>> requestBreakGlass(
            @Valid @RequestBody BreakGlassOverrideRequest request, HttpServletRequest http) {
        AccessOverride override = overrides.requestBreakGlass(new AccessOverrideService.RequestBreakGlassOverride(
                request.siteCode(), request.scopeRef(), request.reason(), request.startsAt(), request.expiresAt(),
                actors.resolve(http)));
        return ResponseEntity.created(URI.create("/api/v1/access-control/overrides/" + override.id()))
                .body(ApiResponse.ok(override));
    }

    @PatchMapping("/overrides/{id}/post-hoc-approval")
    @Operation(summary = "Record the after-the-fact approval for a break-glass override")
    public ApiResponse<AccessOverride> approveBreakGlass(@PathVariable UUID id,
            @Valid @RequestBody PostHocApprovalRequest request, HttpServletRequest http) {
        return ApiResponse.ok(overrides.recordPostHocApproval(id, request.approverId(), actors.resolve(http)));
    }

    @DeleteMapping("/overrides/{id}")
    @Operation(summary = "Revoke an active override before its window ends")
    public ApiResponse<AccessOverride> revokeOverride(@PathVariable UUID id, HttpServletRequest http) {
        return ApiResponse.ok(overrides.revoke(id, actors.resolve(http)));
    }

    @GetMapping("/overrides")
    @Operation(summary = "List a site's currently active overrides")
    public ApiResponse<List<AccessOverride>> activeOverrides(@RequestParam String siteCode, HttpServletRequest http) {
        return ApiResponse.ok(overrides.activeForSite(siteCode, actors.resolve(http)));
    }

    // ---- SOC exception queue (S160a-04) ----

    @PatchMapping("/exceptions/{id}/acknowledgement")
    @Operation(summary = "Acknowledge an exception in the SOC queue")
    public ApiResponse<AccessException> acknowledgeException(@PathVariable UUID id, HttpServletRequest http) {
        return ApiResponse.ok(exceptions.acknowledge(id, actors.resolve(http)));
    }

    @PatchMapping("/exceptions/{id}/resolution")
    @Operation(summary = "Resolve an exception in the SOC queue")
    public ApiResponse<AccessException> resolveException(@PathVariable UUID id, HttpServletRequest http) {
        return ApiResponse.ok(exceptions.resolve(id, actors.resolve(http)));
    }

    @GetMapping("/exceptions")
    @Operation(summary = "The SOC exception queue for a site")
    public ApiResponse<List<AccessException>> exceptionQueue(@RequestParam String siteCode,
            @RequestParam(defaultValue = "OPEN") ExceptionStatus status, HttpServletRequest http) {
        return ApiResponse.ok(exceptions.queue(siteCode, status, actors.resolve(http)));
    }

    // ---- Occupancy and muster (S160a-06) ----

    @GetMapping("/occupancy")
    @Operation(summary = "Current occupancy and in/out state for one zone")
    public ApiResponse<AccessOccupancyService.ZoneOccupancy> occupancy(@RequestParam String siteCode,
            @RequestParam String zoneCode, HttpServletRequest http) {
        return ApiResponse.ok(occupancy.occupancy(siteCode, zoneCode, actors.resolve(http)));
    }

    @GetMapping("/muster")
    @Operation(summary = "Muster/roll-call view across a site's zones",
            description = "Access-derived occupancy only - not yet combined with the S160 on-site visitor "
                    + "population; see the implementation notes.")
    public ApiResponse<List<AccessOccupancyService.ZoneOccupancy>> muster(@RequestParam String siteCode,
            @RequestParam(required = false) List<String> zoneCodes, HttpServletRequest http) {
        return ApiResponse.ok(occupancy.muster(siteCode, zoneCodes, actors.resolve(http)));
    }

    public record DefineZoneRequest(@NotBlank String siteCode, @NotBlank String zoneCode, @NotBlank String name,
            String locationRef, @NotBlank String schedule, Map<String, List<String>> doorGroups) {
    }

    public record RedefineZoneRequest(@NotBlank String schedule, Map<String, List<String>> doorGroups,
            Long expectedVersion) {
    }

    public record EnterExaminationModeRequest(@NotBlank String tightenedSchedule, List<String> lockDoorGroups,
            @NotNull Instant until, Long expectedVersion) {
    }

    public record ExitExaminationModeRequest(@NotBlank String normalSchedule, Long expectedVersion) {
    }

    public record ManualGrantRequest(@NotBlank String siteCode, @NotBlank String personRef,
            @NotBlank String zoneCode, @NotBlank String reason, @NotBlank String approverId,
            @NotNull Instant expiresAt) {
    }

    public record OverrideRequest(@NotBlank String siteCode, @NotBlank String scopeRef, @NotBlank String reason,
            @NotBlank String approverId, @NotNull Instant startsAt, @NotNull Instant expiresAt) {
    }

    public record BreakGlassOverrideRequest(@NotBlank String siteCode, @NotBlank String scopeRef,
            @NotBlank String reason, @NotNull Instant startsAt, @NotNull Instant expiresAt) {
    }

    public record PostHocApprovalRequest(@NotBlank String approverId) {
    }
}
