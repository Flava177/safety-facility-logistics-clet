package gh.edu.clet.sfl.fleetlogistics.fuel.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.FleetActorResolver;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.service.FuelApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.DriverLogbook;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/fuel/logbooks")
@io.swagger.v3.oas.annotations.tags.Tag(name="Driver Logbooks")
public class DriverLogbookController {
    private final FuelApplicationService service;private final FleetActorResolver actors;
    public DriverLogbookController(FuelApplicationService s,FleetActorResolver a){service=s;actors=a;}
    @PostMapping public ResponseEntity<ApiResponse<DriverLogbook>> create(@Valid @RequestBody LogbookRequest r,HttpServletRequest h){var l=service.createLogbook(new FuelApplicationService.CreateLogbook(r.siteCode(),r.driverId(),r.vehicleId(),r.tripId(),r.journeyDate(),r.startTime(),r.endTime(),r.origin(),r.destination(),r.routeNotes(),r.useClassification(),r.purpose(),r.passengerLoadNotes(),r.startOdometer(),r.endOdometer(),r.declarationAccepted(),r.evidenceId(),actors.resolve(h),actors.resolveSourceChannel(h)));return ResponseEntity.created(URI.create("/api/v1/fuel/logbooks/"+l.id())).body(ApiResponse.ok(l));}
    @GetMapping public ApiResponse<List<DriverLogbook>> list(@RequestParam String siteCode,@RequestParam(required=false)DriverLogbook.Status status,@RequestParam(defaultValue="100")int size,HttpServletRequest h){return ApiResponse.ok(service.logbooks(siteCode,status,size,actors.resolve(h)));}
    @GetMapping("/{id}") public ApiResponse<DriverLogbook> detail(@PathVariable UUID id,HttpServletRequest h){return ApiResponse.ok(service.logbook(id,actors.resolve(h)));}
    @PostMapping("/{id}/{action:submit|review|return|approve|reopen|cancel}") public ApiResponse<DriverLogbook> transition(@PathVariable UUID id,@PathVariable String action,@RequestBody(required=false)TransitionRequest r,HttpServletRequest h){return ApiResponse.ok(service.transitionLogbook(id,action,r==null?null:r.comment(),actors.resolve(h),actors.resolveSourceChannel(h)));}
    public record LogbookRequest(@NotBlank String siteCode,@NotNull UUID driverId,@NotNull UUID vehicleId,UUID tripId,@NotNull LocalDate journeyDate,@NotNull Instant startTime,Instant endTime,@NotBlank String origin,@NotBlank String destination,String routeNotes,@NotNull DriverLogbook.UseClassification useClassification,@NotBlank String purpose,String passengerLoadNotes,@PositiveOrZero long startOdometer,Long endOdometer,boolean declarationAccepted,UUID evidenceId){}
    public record TransitionRequest(String comment){}
}
