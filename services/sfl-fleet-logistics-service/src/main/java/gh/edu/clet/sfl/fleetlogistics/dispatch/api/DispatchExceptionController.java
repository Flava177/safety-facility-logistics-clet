package gh.edu.clet.sfl.fleetlogistics.dispatch.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchExceptionService;
import gh.edu.clet.sfl.fleetlogistics.dispatch.api.DispatchPageResponse;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchExceptionCase;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditEvent;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.FleetActorResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

/** S171 accountable dispatch exception/case workflow (custody gaps, receipt variances, scan mismatches). */
@RestController
@RequestMapping("/api/v1/dispatch/exceptions")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Dispatch Exceptions")
public class DispatchExceptionController {

    private final DispatchExceptionService service;
    private final FleetActorResolver actors;

    public DispatchExceptionController(DispatchExceptionService service, FleetActorResolver actors) {
        this.service = service;
        this.actors = actors;
    }

    @GetMapping
    public ApiResponse<DispatchPageResponse<DispatchExceptionCase>> list(@RequestParam String siteCode,
            @RequestParam(required = false) DispatchExceptionCase.Type type,
            @RequestParam(required = false) DispatchExceptionCase.Status status,
            @RequestParam(required = false) DispatchExceptionCase.Severity severity,
            @RequestParam(required = false) String assignee,
            @RequestParam(required = false) Boolean unassigned,
            @RequestParam(required = false) Boolean securityRelevant,
            @RequestParam(required = false) Boolean openOnly,
            @RequestParam(required = false) java.time.Instant dueBefore,
            @RequestParam(required = false) UUID dispatchId,
            @RequestParam(required = false) UUID courierItemId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String sort, HttpServletRequest h) {
        return ApiResponse.ok(DispatchPageResponse.of(service.exceptions(siteCode, type, status, severity, assignee,
                unassigned, securityRelevant, openOnly, dueBefore, dispatchId, courierItemId,
                DispatchPageResponse.paging(page, size, sort), actors.resolve(h))));
    }

    /** The case's transition history: assignment, review, explanation, decision, escalation, closure. */
    @GetMapping("/{id}/history")
    public ApiResponse<List<AuditEvent>> history(@PathVariable UUID id, HttpServletRequest h) {
        return ApiResponse.ok(service.history(id, actors.resolve(h)));
    }

    @GetMapping("/{id}")
    public ApiResponse<DispatchExceptionCase> detail(@PathVariable UUID id, HttpServletRequest h) {
        return ApiResponse.ok(service.exceptionCase(id, actors.resolve(h)));
    }

    @PostMapping("/{id}/{action:assign|reassign|review|request-explanation|explain|approve|reject|escalate|hold|resume|cancel|close|reopen}")
    public ApiResponse<DispatchExceptionCase> transition(@PathVariable UUID id, @PathVariable String action,
            @RequestBody(required = false) ActionRequest r, HttpServletRequest h) {
        return ApiResponse.ok(service.transition(id, action, r == null ? null : r.value(),
                r == null ? null : r.evidenceId(), actors.resolve(h), actors.resolveSourceChannel(h)));
    }

    public record ActionRequest(String value, UUID evidenceId) {}
}
