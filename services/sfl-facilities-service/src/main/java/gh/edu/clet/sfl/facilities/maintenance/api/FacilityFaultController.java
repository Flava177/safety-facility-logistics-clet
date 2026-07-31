package gh.edu.clet.sfl.facilities.maintenance.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.maintenance.application.FacilityFaultService;
import gh.edu.clet.sfl.facilities.maintenance.application.MaintenanceCommands;
import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFaultStatus;
import gh.edu.clet.sfl.facilities.shared.api.FacilitiesActorResolver;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
 * Fault reporting and triage — SRS-SFL-S153-01, -02.
 *
 * <p>The path is unchanged from the pre-S152 version, deliberately: {@code /api/v1/facilities/faults}
 * is printed in the S153 guide and in runbooks. Everything underneath changed — the actor now comes
 * from {@link FacilitiesActorResolver} rather than a raw {@code X-SFL-User} header, every handler is
 * authorised and site-scoped, and every response carries the platform envelope.
 */
@RestController
@RequestMapping("/api/v1/facilities/faults")
@Tag(name = "S153 Faults", description = "Reporting, triage and dismissal of facility faults")
public class FacilityFaultController {

    private final FacilityFaultService service;
    private final FacilitiesActorResolver actorResolver;
    private final Clock clock;

    public FacilityFaultController(FacilityFaultService service, FacilitiesActorResolver actorResolver,
            Clock clock) {
        this.service = service;
        this.actorResolver = actorResolver;
        this.clock = clock;
    }

    @PostMapping
    @Operation(summary = "Report a facility fault",
            description = "SRS-SFL-S153-01. A fault needs either a room or a location code: one with "
                    + "only a site cannot be dispatched anywhere.")
    public ResponseEntity<ApiResponse<MaintenanceResponses.FaultResponse>> report(
            @Valid @RequestBody MaintenanceRequests.ReportFault request, HttpServletRequest http) {
        MaintenanceResponses.FaultResponse result = MaintenanceResponses.FaultResponse.from(
                service.report(new MaintenanceCommands.ReportFault(request.siteCode(), request.roomId(),
                        request.locationCode(), request.assetId(), request.title(), request.description(),
                        request.category(), request.priority(), actor(http), channel(http),
                        idempotencyKey(http), request)),
                clock);
        return ResponseEntity.created(URI.create("/api/v1/facilities/faults/" + result.id()))
                .body(ApiResponse.ok(result));
    }

    @GetMapping
    @Operation(summary = "Search faults",
            description = "Filtered by site, space, status and openness. A requester sees only the "
                    + "faults they reported, whatever the filters say.")
    public ApiResponse<List<MaintenanceResponses.FaultResponse>> search(
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) UUID roomId,
            @RequestParam(required = false) FacilityFaultStatus status,
            @RequestParam(required = false) Boolean openOnly,
            @RequestParam(defaultValue = "100") int limit,
            HttpServletRequest http) {
        return ApiResponse.ok(service.search(siteCode, roomId, status, openOnly, limit, actor(http),
                        channel(http)).stream()
                .map(fault -> MaintenanceResponses.FaultResponse.from(fault, clock))
                .toList());
    }

    @GetMapping("/{faultId}")
    @Operation(summary = "Read one fault")
    public ApiResponse<MaintenanceResponses.FaultResponse> findById(@PathVariable UUID faultId,
            HttpServletRequest http) {
        return ApiResponse.ok(MaintenanceResponses.FaultResponse.from(
                service.findById(faultId, actor(http), channel(http)), clock));
    }

    @PatchMapping("/{faultId}/triage")
    @Operation(summary = "Triage a fault and start its SLA clock",
            description = "SRS-SFL-S153-02. The SLA is computed from the configuration active now and "
                    + "the site's current operating mode. Priority may be corrected here and only here.")
    public ApiResponse<MaintenanceResponses.FaultResponse> triage(@PathVariable UUID faultId,
            @Valid @RequestBody MaintenanceRequests.TriageFault request, HttpServletRequest http) {
        return ApiResponse.ok(MaintenanceResponses.FaultResponse.from(
                service.triage(new MaintenanceCommands.TriageFault(faultId, request.priority(),
                        request.notes(), request.expectedVersion(), actor(http), channel(http))),
                clock));
    }

    @PatchMapping("/{faultId}/dismissal")
    @Operation(summary = "Reject, duplicate or withdraw a fault",
            description = "All three are terminal and all three require a reason. A duplicate must name "
                    + "the fault it duplicates.")
    public ApiResponse<MaintenanceResponses.FaultResponse> dismiss(@PathVariable UUID faultId,
            @Valid @RequestBody MaintenanceRequests.DismissFault request, HttpServletRequest http) {
        return ApiResponse.ok(MaintenanceResponses.FaultResponse.from(
                service.dismiss(new MaintenanceCommands.DismissFault(faultId, request.outcome(),
                        request.reason(), request.duplicateOfFaultId(), request.expectedVersion(),
                        actor(http), channel(http))),
                clock));
    }

    @PatchMapping("/{faultId}/lifecycle")
    @Operation(summary = "Change a fault's record lifecycle state")
    public ApiResponse<MaintenanceResponses.FaultResponse> changeLifecycle(@PathVariable UUID faultId,
            @Valid @RequestBody MaintenanceRequests.ChangeLifecycle request, HttpServletRequest http) {
        return ApiResponse.ok(MaintenanceResponses.FaultResponse.from(
                service.changeLifecycle(new MaintenanceCommands.ChangeFaultLifecycle(faultId,
                        request.lifecycleStatus(), request.expectedVersion(), actor(http), channel(http))),
                clock));
    }

    @GetMapping("/rooms/{roomId}")
    @Operation(summary = "Open faults on one space",
            description = "What the S152 space-detail screen shows beside the readiness blockers.")
    public ApiResponse<List<MaintenanceResponses.FaultResponse>> forRoom(@PathVariable UUID roomId,
            HttpServletRequest http) {
        return ApiResponse.ok(service.openFaultsForRoom(roomId, actor(http), channel(http)).stream()
                .map(fault -> MaintenanceResponses.FaultResponse.from(fault, clock))
                .toList());
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
