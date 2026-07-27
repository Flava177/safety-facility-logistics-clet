package gh.edu.clet.sfl.fleetlogistics.dispatch.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchExceptionService;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchExceptionCase;
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
    public ApiResponse<List<DispatchExceptionCase>> list(@RequestParam String siteCode,
            @RequestParam(required = false) DispatchExceptionCase.Type type,
            @RequestParam(required = false) DispatchExceptionCase.Status status,
            @RequestParam(defaultValue = "100") int size, HttpServletRequest h) {
        return ApiResponse.ok(service.exceptions(siteCode, type, status, size, actors.resolve(h)));
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
