package gh.edu.clet.sfl.facilities.maintenance.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.maintenance.application.MaintenanceCommands;
import gh.edu.clet.sfl.facilities.maintenance.application.MaintenanceEscalationService;
import gh.edu.clet.sfl.facilities.maintenance.application.MaintenanceVendorService;
import gh.edu.clet.sfl.facilities.maintenance.application.PreventiveMaintenanceService;
import gh.edu.clet.sfl.facilities.shared.api.FacilitiesActorResolver;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
 * Planning: vendors, preventive schedules, and the two sweeps.
 *
 * <p>Three small registers under one controller rather than three controllers with one endpoint
 * group each. They share an audience — whoever plans maintenance rather than performs it — and
 * splitting them would produce three files whose only difference is the noun.
 *
 * <p>The two {@code /runs} endpoints exist so a sweep can be triggered by hand. They do exactly what
 * the scheduler does, are idempotent for the same reasons, and return what moved — which is what
 * makes them useful for an operator checking whether the automation is working rather than reading
 * a log to find out.
 */
@RestController
@RequestMapping("/api/v1/facilities/maintenance")
@Tag(name = "S153 Planning", description = "Vendors, preventive schedules, generation and escalation")
public class MaintenancePlanningController {

    private final MaintenanceVendorService vendors;
    private final PreventiveMaintenanceService schedules;
    private final MaintenanceEscalationService escalation;
    private final FacilitiesActorResolver actorResolver;
    private final Clock clock;

    public MaintenancePlanningController(MaintenanceVendorService vendors,
            PreventiveMaintenanceService schedules, MaintenanceEscalationService escalation,
            FacilitiesActorResolver actorResolver, Clock clock) {
        this.vendors = vendors;
        this.schedules = schedules;
        this.escalation = escalation;
        this.actorResolver = actorResolver;
        this.clock = clock;
    }

    // ---- vendors ------------------------------------------------------------------------------

    @PostMapping("/vendors")
    @Operation(summary = "Register a maintenance vendor",
            description = "A local reference, not the procurement master. externalVendorId carries "
                    + "procurement's identifier for the same company.")
    public ResponseEntity<ApiResponse<MaintenanceResponses.VendorResponse>> registerVendor(
            @Valid @RequestBody MaintenanceRequests.RegisterVendor request, HttpServletRequest http) {
        MaintenanceResponses.VendorResponse result = MaintenanceResponses.VendorResponse.from(
                vendors.register(new MaintenanceCommands.RegisterVendor(request.siteCode(),
                        request.vendorCode(), request.name(), request.specialisation(),
                        request.contactName(), request.contactEmail(), request.contactPhone(),
                        request.responseHours(), request.contractReference(), request.contractExpiresOn(),
                        request.externalVendorId(), actor(http), channel(http), idempotencyKey(http),
                        request)),
                today());
        return ResponseEntity.created(URI.create("/api/v1/facilities/maintenance/vendors/" + result.id()))
                .body(ApiResponse.ok(result));
    }

    @GetMapping("/vendors")
    @Operation(summary = "List maintenance vendors, optionally for one site")
    public ApiResponse<List<MaintenanceResponses.VendorResponse>> listVendors(
            @RequestParam(required = false) String siteCode, HttpServletRequest http) {
        LocalDate today = today();
        return ApiResponse.ok(vendors.list(siteCode, actor(http), channel(http)).stream()
                .map(vendor -> MaintenanceResponses.VendorResponse.from(vendor, today))
                .toList());
    }

    @GetMapping("/vendors/{vendorId}")
    @Operation(summary = "Read one vendor")
    public ApiResponse<MaintenanceResponses.VendorResponse> vendor(@PathVariable UUID vendorId,
            HttpServletRequest http) {
        return ApiResponse.ok(MaintenanceResponses.VendorResponse.from(
                vendors.findById(vendorId, actor(http), channel(http)), today()));
    }

    @PatchMapping("/vendors/{vendorId}")
    @Operation(summary = "Update a vendor's contact and contract details")
    public ApiResponse<MaintenanceResponses.VendorResponse> updateVendor(@PathVariable UUID vendorId,
            @Valid @RequestBody MaintenanceRequests.UpdateVendor request, HttpServletRequest http) {
        return ApiResponse.ok(MaintenanceResponses.VendorResponse.from(
                vendors.update(new MaintenanceCommands.UpdateVendor(vendorId, request.name(),
                        request.specialisation(), request.contactName(), request.contactEmail(),
                        request.contactPhone(), request.responseHours(), request.contractReference(),
                        request.contractExpiresOn(), request.expectedVersion(), actor(http), channel(http))),
                today()));
    }

    @PatchMapping("/vendors/{vendorId}/lifecycle")
    @Operation(summary = "Suspend, retire or reactivate a vendor")
    public ApiResponse<MaintenanceResponses.VendorResponse> changeVendorLifecycle(@PathVariable UUID vendorId,
            @Valid @RequestBody MaintenanceRequests.ChangeLifecycle request, HttpServletRequest http) {
        return ApiResponse.ok(MaintenanceResponses.VendorResponse.from(
                vendors.changeLifecycle(new MaintenanceCommands.ChangeVendorLifecycle(vendorId,
                        request.lifecycleStatus(), request.expectedVersion(), actor(http), channel(http))),
                today()));
    }

    // ---- preventive schedules -------------------------------------------------------------------

    @PostMapping("/schedules")
    @Operation(summary = "Create a preventive maintenance schedule",
            description = "Generates a work order leadTimeDays before each due date. The lead time must "
                    + "be shorter than the interval, or the queue fills with overlapping duplicates.")
    public ResponseEntity<ApiResponse<MaintenanceResponses.ScheduleResponse>> createSchedule(
            @Valid @RequestBody MaintenanceRequests.CreateSchedule request, HttpServletRequest http) {
        MaintenanceResponses.ScheduleResponse result = MaintenanceResponses.ScheduleResponse.from(
                schedules.create(new MaintenanceCommands.CreateSchedule(request.siteCode(),
                        request.scheduleCode(), request.name(), request.description(), request.assetId(),
                        request.intervalDays(), request.leadTimeDays(), request.priority(),
                        request.workOrderType(), request.firstDueOn(), actor(http), channel(http),
                        idempotencyKey(http), request)),
                today());
        return ResponseEntity.created(URI.create("/api/v1/facilities/maintenance/schedules/" + result.id()))
                .body(ApiResponse.ok(result));
    }

    @GetMapping("/schedules")
    @Operation(summary = "List preventive schedules, optionally by site or asset")
    public ApiResponse<List<MaintenanceResponses.ScheduleResponse>> listSchedules(
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) UUID assetId,
            HttpServletRequest http) {
        LocalDate today = today();
        return ApiResponse.ok(schedules.list(siteCode, assetId, actor(http), channel(http)).stream()
                .map(schedule -> MaintenanceResponses.ScheduleResponse.from(schedule, today))
                .toList());
    }

    @GetMapping("/schedules/{scheduleId}")
    @Operation(summary = "Read one preventive schedule")
    public ApiResponse<MaintenanceResponses.ScheduleResponse> schedule(@PathVariable UUID scheduleId,
            HttpServletRequest http) {
        return ApiResponse.ok(MaintenanceResponses.ScheduleResponse.from(
                schedules.findById(scheduleId, actor(http), channel(http)), today()));
    }

    @PatchMapping("/schedules/{scheduleId}")
    @Operation(summary = "Update a preventive schedule")
    public ApiResponse<MaintenanceResponses.ScheduleResponse> updateSchedule(@PathVariable UUID scheduleId,
            @Valid @RequestBody MaintenanceRequests.UpdateSchedule request, HttpServletRequest http) {
        return ApiResponse.ok(MaintenanceResponses.ScheduleResponse.from(
                schedules.update(new MaintenanceCommands.UpdateSchedule(scheduleId, request.name(),
                        request.description(), request.intervalDays(), request.leadTimeDays(),
                        request.priority(), request.nextDueOn(), request.expectedVersion(), actor(http),
                        channel(http))),
                today()));
    }

    @PatchMapping("/schedules/{scheduleId}/lifecycle")
    @Operation(summary = "Suspend, retire or reactivate a preventive schedule")
    public ApiResponse<MaintenanceResponses.ScheduleResponse> changeScheduleLifecycle(
            @PathVariable UUID scheduleId, @Valid @RequestBody MaintenanceRequests.ChangeLifecycle request,
            HttpServletRequest http) {
        return ApiResponse.ok(MaintenanceResponses.ScheduleResponse.from(
                schedules.changeLifecycle(new MaintenanceCommands.ChangeScheduleLifecycle(scheduleId,
                        request.lifecycleStatus(), request.expectedVersion(), actor(http), channel(http))),
                today()));
    }

    // ---- sweeps -------------------------------------------------------------------------------

    @PostMapping("/schedules/runs")
    @Operation(summary = "Generate the preventive work orders due today",
            description = "What the scheduler does, on demand. Idempotent by cycle: a schedule already "
                    + "generated for its current due date produces nothing, however often this is called.")
    public ApiResponse<MaintenanceResponses.GenerationRunResponse> generate(HttpServletRequest http) {
        LocalDate today = today();
        var raised = schedules.generateDueWorkOrders(actor(http), today);
        return ApiResponse.ok(new MaintenanceResponses.GenerationRunResponse(today, raised.size(),
                raised.stream()
                        .map(order -> MaintenanceResponses.WorkOrderResponse.from(order, clock))
                        .toList()));
    }

    @PostMapping("/escalations/runs")
    @Operation(summary = "Escalate everything past its SLA",
            description = "SRS-SFL-S153-02. Evaluated against the configuration active right now. "
                    + "Idempotent: an item already at the level it is owed does not move.")
    public ApiResponse<MaintenanceResponses.EscalationSweepResponse> escalate(HttpServletRequest http) {
        var sweep = escalation.sweep(actor(http));
        return ApiResponse.ok(new MaintenanceResponses.EscalationSweepResponse(sweep.evaluatedAt(),
                sweep.faultsEscalated(), sweep.workOrdersEscalated(), sweep.total()));
    }

    // ---- internals ----------------------------------------------------------------------------

    private LocalDate today() {
        return clock.instant().atZone(ZoneOffset.UTC).toLocalDate();
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
