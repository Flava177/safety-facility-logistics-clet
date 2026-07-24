package gh.edu.clet.sfl.fleetlogistics.fuel.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.FleetActorResolver;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.service.FuelApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelAnomalyCase;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/fuel/anomalies")
@io.swagger.v3.oas.annotations.tags.Tag(name="Fuel Anomalies")
public class FuelAnomalyController {
    private final FuelApplicationService service;private final FleetActorResolver actors;
    public FuelAnomalyController(FuelApplicationService s,FleetActorResolver a){service=s;actors=a;}
    @GetMapping public ApiResponse<List<FuelAnomalyCase>> list(@RequestParam String siteCode,@RequestParam(required=false)FuelAnomalyCase.Status status,@RequestParam(defaultValue="100")int size,HttpServletRequest h){return ApiResponse.ok(service.anomalies(siteCode,status,size,actors.resolve(h)));}
    @GetMapping("/{id}") public ApiResponse<FuelAnomalyCase> detail(@PathVariable UUID id,HttpServletRequest h){return ApiResponse.ok(service.anomaly(id,actors.resolve(h)));}
    @PostMapping("/{id}/{action:assign|reassign|review|request-explanation|explain|approve|reject|escalate|hold|resume|cancel|close|reopen}") public ApiResponse<FuelAnomalyCase> transition(@PathVariable UUID id,@PathVariable String action,@RequestBody(required=false)ActionRequest r,HttpServletRequest h){return ApiResponse.ok(service.transitionAnomaly(id,action,r==null?null:r.value(),r==null?null:r.evidenceId(),actors.resolve(h),actors.resolveSourceChannel(h)));}
    public record ActionRequest(String value,UUID evidenceId){}
}
