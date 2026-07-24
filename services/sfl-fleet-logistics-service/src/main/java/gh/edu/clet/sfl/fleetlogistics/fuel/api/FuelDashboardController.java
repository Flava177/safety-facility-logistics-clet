package gh.edu.clet.sfl.fleetlogistics.fuel.api;
import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.FleetActorResolver;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.service.FuelApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/fuel/dashboard") @io.swagger.v3.oas.annotations.tags.Tag(name="Fuel Dashboards and Reports")
public class FuelDashboardController {private final FuelApplicationService service;private final FleetActorResolver actors;public FuelDashboardController(FuelApplicationService s,FleetActorResolver a){service=s;actors=a;}@GetMapping public ApiResponse<Map<String,Object>> dashboard(@RequestParam String siteCode,HttpServletRequest h){return ApiResponse.ok(service.dashboard(siteCode,actors.resolve(h)));}}
