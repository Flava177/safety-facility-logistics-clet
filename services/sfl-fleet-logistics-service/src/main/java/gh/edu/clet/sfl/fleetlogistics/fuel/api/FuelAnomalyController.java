package gh.edu.clet.sfl.fleetlogistics.fuel.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.FleetActorResolver;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditEvent;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.service.FuelApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelAnomalyCase;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/fuel/anomalies")
@io.swagger.v3.oas.annotations.tags.Tag(name="Fuel Anomalies")
public class FuelAnomalyController {
    private final FuelApplicationService service;private final FleetActorResolver actors;
    public FuelAnomalyController(FuelApplicationService s,FleetActorResolver a){service=s;actors=a;}

    /**
     * The anomaly queue, filtered server-side.
     *
     * <p>Only {@code status} used to reach the query; type, severity, assignee, materiality and SLA
     * standing were all applied by the client to whatever window came back. That made a "breaching
     * SLA" view mean "breaches among the first hundred cases", which is exactly the queue an
     * operator must not be given. {@code openOnly} and {@code dueBefore} together are what make the
     * SLA view a real query — {@code dueBefore} existed on the repository and was reachable only
     * from the sweep scheduler.
     */
    @GetMapping public ApiResponse<FuelPageResponse<FuelAnomalyCase>> list(
            @RequestParam String siteCode,
            @RequestParam(required=false)FuelAnomalyCase.Status status,
            @RequestParam(required=false)FuelAnomalyCase.Type type,
            @RequestParam(required=false)FuelAnomalyCase.Severity severity,
            @RequestParam(required=false)String assignee,
            @RequestParam(required=false)Boolean unassigned,
            @RequestParam(required=false)Boolean material,
            @RequestParam(required=false)Boolean openOnly,
            @RequestParam(required=false)Instant dueBefore,
            @RequestParam(required=false)UUID transactionId,
            @RequestParam(defaultValue="0")int page,
            @RequestParam(defaultValue="25")int size,
            @RequestParam(required=false)String sort,
            HttpServletRequest h){
        return ApiResponse.ok(FuelPageResponse.of(service.anomalies(siteCode,status,type,severity,assignee,
                unassigned,material,openOnly,dueBefore,transactionId,
                FuelPageResponse.paging(page,size,sort),actors.resolve(h))));
    }

    @GetMapping("/{id}") public ApiResponse<FuelAnomalyCase> detail(@PathVariable UUID id,HttpServletRequest h){return ApiResponse.ok(service.anomaly(id,actors.resolve(h)));}

    /** The case's transition history: assignment, explanation, decision, escalation and closure. */
    @GetMapping("/{id}/history") public ApiResponse<List<AuditEvent>> history(@PathVariable UUID id,HttpServletRequest h){
        return ApiResponse.ok(service.history("FuelAnomalyCase",id,actors.resolve(h)));
    }

    @PostMapping("/{id}/{action:assign|reassign|review|request-explanation|explain|approve|reject|escalate|hold|resume|cancel|close|reopen}") public ApiResponse<FuelAnomalyCase> transition(@PathVariable UUID id,@PathVariable String action,@RequestBody(required=false)ActionRequest r,HttpServletRequest h){return ApiResponse.ok(service.transitionAnomaly(id,action,r==null?null:r.value(),r==null?null:r.evidenceId(),actors.resolve(h),actors.resolveSourceChannel(h)));}

    public record ActionRequest(String value,UUID evidenceId){}
}
