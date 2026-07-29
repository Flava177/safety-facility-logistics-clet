package gh.edu.clet.sfl.fleetlogistics.fuel.api;
import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.FleetActorResolver;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.service.FuelApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.port.FuelRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/fuel/dashboard") @io.swagger.v3.oas.annotations.tags.Tag(name="Fuel Dashboards and Reports")
public class FuelDashboardController {private final FuelApplicationService service;private final FleetActorResolver actors;public FuelDashboardController(FuelApplicationService s,FleetActorResolver a){service=s;actors=a;}@GetMapping public ApiResponse<Map<String,Object>> dashboard(@RequestParam String siteCode,HttpServletRequest h){return ApiResponse.ok(service.dashboard(siteCode,actors.resolve(h)));}
    /**
     * Fuel spend and volume by day, aggregated by the service.
     *
     * <p>The chart bucketed this in the browser from a page of transactions, so it described that
     * page rather than the site.
     */
    @GetMapping("/daily-totals") public ApiResponse<List<FuelRepository.DailyFuelTotals>> dailyTotals(
            @RequestParam String siteCode,
            @RequestParam(required=false)Instant from,
            @RequestParam(required=false)Instant to,
            HttpServletRequest h){
        return ApiResponse.ok(service.dailyTotals(siteCode,from,to,actors.resolve(h)));
    }

    /** Open anomaly counts by type, so a by-type chart stops reading a page of records. */
    @GetMapping("/anomaly-counts") public ApiResponse<Map<String,Long>> anomalyCounts(@RequestParam String siteCode,
            HttpServletRequest h){
        return ApiResponse.ok(service.anomalyCountsByType(siteCode,actors.resolve(h)));
    }
}
