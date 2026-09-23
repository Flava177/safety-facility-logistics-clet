package gh.edu.clet.sfl.safetysecurity.lifesafety.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.safetysecurity.lifesafety.application.service.DetectorCoverageService;
import gh.edu.clet.sfl.safetysecurity.lifesafety.application.service.FastLaneTriggerService;
import gh.edu.clet.sfl.safetysecurity.lifesafety.application.service.InspectionComplianceService;
import gh.edu.clet.sfl.safetysecurity.lifesafety.application.service.LifeSafetyEventIngestionService;
import gh.edu.clet.sfl.safetysecurity.lifesafety.application.service.MusterService;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.ComplianceExceptionStatus;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.DetectorCoverage;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.DetectorType;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.FastLaneTrigger;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.InspectionSchedule;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.LifeSafetyComplianceException;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.LifeSafetyEvent;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.MusterCheckIn;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.MusterSession;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.TestResult;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** SRS-SFL-S162a: inspections, compliance exceptions, detector coverage and evacuation muster. */
@RestController
@RequestMapping("/api/v1/life-safety")
public class LifeSafetyController {

    private final LifeSafetyEventIngestionService events;
    private final FastLaneTriggerService fastLane;
    private final InspectionComplianceService inspections;
    private final DetectorCoverageService coverage;
    private final MusterService muster;
    private final LifeSafetyActorResolver actors;

    public LifeSafetyController(LifeSafetyEventIngestionService events, FastLaneTriggerService fastLane,
            InspectionComplianceService inspections, DetectorCoverageService coverage, MusterService muster,
            LifeSafetyActorResolver actors) {
        this.events = events;
        this.fastLane = fastLane;
        this.inspections = inspections;
        this.coverage = coverage;
        this.muster = muster;
        this.actors = actors;
    }

    @GetMapping("/events")
    @Operation(summary = "List observed life-safety events for a site")
    public ApiResponse<List<LifeSafetyEvent>> events(@RequestParam String siteCode,
            @RequestParam(defaultValue = "50") @Positive int limit, HttpServletRequest http) {
        return ApiResponse.ok(events.list(siteCode, limit, actors.resolve(http)));
    }

    @GetMapping("/fast-lane-triggers")
    @Operation(summary = "List fast-lane emergency triggers raised from life-safety events")
    public ApiResponse<List<FastLaneTrigger>> fastLaneTriggers(@RequestParam String siteCode,
            @RequestParam(defaultValue = "50") @Positive int limit, HttpServletRequest http) {
        return ApiResponse.ok(fastLane.list(siteCode, limit, actors.resolve(http)));
    }

    public record RegisterScheduleRequest(@NotBlank String systemRef, String description,
            @Positive int frequencyDays) {
    }

    @PostMapping("/inspection-schedules")
    @Operation(summary = "Register an inspection/certification schedule for a life-safety system")
    public InspectionSchedule registerSchedule(@RequestParam String siteCode,
            @RequestBody RegisterScheduleRequest body, HttpServletRequest http) {
        return inspections.register(new InspectionComplianceService.RegisterSchedule(siteCode, body.systemRef(),
                body.description(), body.frequencyDays(), actors.resolve(http)));
    }

    @GetMapping("/inspection-schedules")
    @Operation(summary = "List inspection schedules for a site")
    public ApiResponse<List<InspectionSchedule>> listSchedules(@RequestParam String siteCode,
            HttpServletRequest http) {
        return ApiResponse.ok(inspections.listSchedules(siteCode, actors.resolve(http)));
    }

    public record RecordPerformedRequest(String evidenceReference) {
    }

    @PostMapping("/inspection-schedules/{id}/performed")
    @Operation(summary = "Record that a scheduled inspection was performed, resolving any overdue exception")
    public InspectionSchedule recordPerformed(@PathVariable UUID id, @RequestBody RecordPerformedRequest body,
            HttpServletRequest http) {
        return inspections.recordPerformed(id, body.evidenceReference(), actors.resolve(http));
    }

    public record PanelFaultRequest(@NotBlank String deviceRef, String note) {
    }

    @PostMapping("/compliance-exceptions/panel-fault")
    @Operation(summary = "Report a panel fault, raising a compliance exception")
    public LifeSafetyComplianceException reportPanelFault(@RequestParam String siteCode,
            @RequestBody PanelFaultRequest body, HttpServletRequest http) {
        return inspections.reportPanelFault(siteCode, body.deviceRef(), body.note(), actors.resolve(http));
    }

    @GetMapping("/compliance-exceptions")
    @Operation(summary = "List life-safety compliance exceptions for a site")
    public ApiResponse<List<LifeSafetyComplianceException>> listExceptions(@RequestParam String siteCode,
            @RequestParam(required = false) ComplianceExceptionStatus status, HttpServletRequest http) {
        return ApiResponse.ok(inspections.listExceptions(siteCode, status, actors.resolve(http)));
    }

    @PostMapping("/compliance-exceptions/{id}/resolve")
    @Operation(summary = "Resolve a life-safety compliance exception")
    public LifeSafetyComplianceException resolveException(@PathVariable UUID id, HttpServletRequest http) {
        return inspections.resolve(id, actors.resolve(http));
    }

    public record RegisterDeviceRequest(@NotBlank String zoneCode, @NotBlank String deviceRef,
            @NotNull DetectorType deviceType, @Positive int testFrequencyDays) {
    }

    @PostMapping("/detector-coverage")
    @Operation(summary = "Register a detector/panic-device/siren mapped to a zone")
    public DetectorCoverage registerDevice(@RequestParam String siteCode, @RequestBody RegisterDeviceRequest body,
            HttpServletRequest http) {
        return coverage.register(new DetectorCoverageService.RegisterDevice(siteCode, body.zoneCode(),
                body.deviceRef(), body.deviceType(), body.testFrequencyDays(), actors.resolve(http)));
    }

    @GetMapping("/detector-coverage")
    @Operation(summary = "List detector coverage for a site")
    public ApiResponse<List<DetectorCoverage>> listCoverage(@RequestParam String siteCode, HttpServletRequest http) {
        return ApiResponse.ok(coverage.list(siteCode, actors.resolve(http)));
    }

    public record RecordTestRequest(@NotNull TestResult result, @Positive int nextTestFrequencyDays) {
    }

    @PostMapping("/detector-coverage/{id}/test")
    @Operation(summary = "Record a functional test result for a detector")
    public DetectorCoverage recordTest(@PathVariable UUID id, @RequestBody RecordTestRequest body,
            HttpServletRequest http) {
        return coverage.recordTest(id, body.result(), body.nextTestFrequencyDays(), actors.resolve(http));
    }

    public record CheckInRequest(@NotBlank String personRef) {
    }

    @PostMapping("/muster/{sessionId}/check-in")
    @Operation(summary = "Check a person in at the muster point during an evacuation")
    public MusterCheckIn checkIn(@PathVariable UUID sessionId, @RequestBody CheckInRequest body,
            HttpServletRequest http) {
        return muster.checkIn(sessionId, body.personRef(), actors.resolve(http));
    }

    @GetMapping("/muster/{sessionId}")
    @Operation(summary = "The roll-call view for an open muster session: checked-in and outstanding persons")
    public MusterService.RollCallView rollCall(@PathVariable UUID sessionId, HttpServletRequest http) {
        return muster.rollCall(sessionId, actors.resolve(http));
    }

    @PostMapping("/muster/{sessionId}/close")
    @Operation(summary = "Close a muster session once everyone is accounted for")
    public MusterSession closeMuster(@PathVariable UUID sessionId, HttpServletRequest http) {
        return muster.close(sessionId, actors.resolve(http));
    }
}
