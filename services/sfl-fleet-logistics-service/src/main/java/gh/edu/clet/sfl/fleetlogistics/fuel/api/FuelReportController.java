package gh.edu.clet.sfl.fleetlogistics.fuel.api;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.FleetActorResolver;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.service.FuelApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/fuel/reports") @io.swagger.v3.oas.annotations.tags.Tag(name="Fuel Dashboards and Reports")
public class FuelReportController {private final FuelApplicationService service;private final FleetActorResolver actors;public FuelReportController(FuelApplicationService s,FleetActorResolver a){service=s;actors=a;}@GetMapping(value="/transactions.csv",produces="text/csv")public ResponseEntity<String> transactions(@RequestParam String siteCode,HttpServletRequest h){return ResponseEntity.ok().contentType(new MediaType("text","csv")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=fuel-transactions-"+siteCode+".csv").body(service.transactionReportCsv(siteCode,actors.resolve(h)));}}
