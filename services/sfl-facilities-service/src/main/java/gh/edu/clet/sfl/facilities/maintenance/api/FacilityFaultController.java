package gh.edu.clet.sfl.facilities.maintenance.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.maintenance.application.FacilityFaultService;
import gh.edu.clet.sfl.facilities.maintenance.application.MaintenanceCommands;
import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFaultStatus;
import gh.edu.clet.sfl.facilities.shared.api.FacilitiesActorResolver;
import gh.edu.clet.sfl.facilities.shared.api.IdempotencyKey;
import gh.edu.clet.sfl.facilities.shared.api.PageResponse;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
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

/**
 * Fault reporting and triage - SRS-SFL-S153-01, -02.
 *
 * <p>The path is unchanged from the pre-S152 version, deliberately: {@code /api/v1/facilities/faults}
 * is printed in the S153 guide and in runbooks. Everything underneath changed - the actor now comes
 * from {@link FacilitiesActorResolver} rather than a raw {@code X-SFL-User} header, every handler is
 * authorised and site-scoped, and every response carries the platform envelope.
 */
@RestController
@RequestMapping("/api/v1/facilities/faults")
@Tag(name = "S153 Faults", description = "Reporting, triage and dismissal of facility faults")
public class FacilityFaultController {

    private final FacilityFaultService service;
    private final Clock clock;

    public FacilityFaultController(FacilityFaultService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @PostMapping
    @Operation(summary = "Report a facility fault",
            description = "SRS-SFL-S153-01. A fault needs either a room or a location code: one with "
                    + "only a site cannot be dispatched anywhere.")
    public ResponseEntity<ApiResponse<MaintenanceResponses.FaultResponse>> report(
            @Valid @RequestBody MaintenanceRequests.ReportFault request, ActorContext actor,
            SourceChannel channel, @IdempotencyKey String idempotencyKey) {
        MaintenanceResponses.FaultResponse result = MaintenanceResponses.FaultResponse.from(
                service.report(new MaintenanceCommands.ReportFault(request.siteCode(), request.roomId(),
                        request.locationCode(), request.assetId(), request.title(), request.description(),
                        request.category(), request.priority(), actor, channel,
                        idempotencyKey, request)),
                clock);
        return ResponseEntity.created(URI.create("/api/v1/facilities/faults/" + result.id()))
                .body(ApiResponse.ok(result));
    }

    @GetMapping
    @Operation(summary = "Search faults",
            description = "Filtered by site, space, status and openness. A requester sees only the "
                    + "faults they reported, whatever the filters say.")
    public ApiResponse<PageResponse<MaintenanceResponses.FaultResponse>> search(
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) UUID roomId,
            @RequestParam(required = false) FacilityFaultStatus status,
            @RequestParam(required = false) Boolean openOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(PageResponse.from(
                service.search(siteCode, roomId, status, openOnly, page, size, actor, channel),
                fault -> MaintenanceResponses.FaultResponse.from(fault, clock)));
    }

    @GetMapping("/{faultId}")
    @Operation(summary = "Read one fault")
    public ApiResponse<MaintenanceResponses.FaultResponse> findById(@PathVariable UUID faultId,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(MaintenanceResponses.FaultResponse.from(
                service.findById(faultId, actor, channel), clock));
    }

    @PatchMapping("/{faultId}/triage")
    @Operation(summary = "Triage a fault and start its SLA clock",
            description = "SRS-SFL-S153-02. The SLA is computed from the configuration active now and "
                    + "the site's current operating mode. Priority may be corrected here and only here.")
    public ApiResponse<MaintenanceResponses.FaultResponse> triage(@PathVariable UUID faultId,
            @Valid @RequestBody MaintenanceRequests.TriageFault request, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(MaintenanceResponses.FaultResponse.from(
                service.triage(new MaintenanceCommands.TriageFault(faultId, request.priority(),
                        request.notes(), request.expectedVersion(), actor, channel)),
                clock));
    }

    @PatchMapping("/{faultId}/dismissal")
    @Operation(summary = "Reject, duplicate or withdraw a fault",
            description = "All three are terminal and all three require a reason. A duplicate must name "
                    + "the fault it duplicates.")
    public ApiResponse<MaintenanceResponses.FaultResponse> dismiss(@PathVariable UUID faultId,
            @Valid @RequestBody MaintenanceRequests.DismissFault request, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(MaintenanceResponses.FaultResponse.from(
                service.dismiss(new MaintenanceCommands.DismissFault(faultId, request.outcome(),
                        request.reason(), request.duplicateOfFaultId(), request.expectedVersion(),
                        actor, channel)),
                clock));
    }

    @PatchMapping("/{faultId}/lifecycle")
    @Operation(summary = "Change a fault's record lifecycle state")
    public ApiResponse<MaintenanceResponses.FaultResponse> changeLifecycle(@PathVariable UUID faultId,
            @Valid @RequestBody MaintenanceRequests.ChangeLifecycle request, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(MaintenanceResponses.FaultResponse.from(
                service.changeLifecycle(new MaintenanceCommands.ChangeFaultLifecycle(faultId,
                        request.lifecycleStatus(), request.expectedVersion(), actor, channel)),
                clock));
    }

    @GetMapping("/rooms/{roomId}")
    @Operation(summary = "Open faults on one space",
            description = "What the S152 space-detail screen shows beside the readiness blockers.")
    public ApiResponse<List<MaintenanceResponses.FaultResponse>> forRoom(@PathVariable UUID roomId,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(service.openFaultsForRoom(roomId, actor, channel).stream()
                .map(fault -> MaintenanceResponses.FaultResponse.from(fault, clock))
                .toList());
    }
}
