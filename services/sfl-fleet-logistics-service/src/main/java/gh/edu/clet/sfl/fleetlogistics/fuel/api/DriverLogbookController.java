package gh.edu.clet.sfl.fleetlogistics.fuel.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.FleetActorResolver;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditEvent;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/fuel/logbooks")
@io.swagger.v3.oas.annotations.tags.Tag(name="Driver Logbooks")
public class DriverLogbookController {
    private final FuelApplicationService service;private final FleetActorResolver actors;
    public DriverLogbookController(FuelApplicationService s,FleetActorResolver a){service=s;actors=a;}

    @PostMapping public ResponseEntity<ApiResponse<DriverLogbook>> create(@Valid @RequestBody LogbookRequest r,HttpServletRequest h){var l=service.createLogbook(new FuelApplicationService.CreateLogbook(r.siteCode(),r.driverId(),r.vehicleId(),r.tripId(),r.journeyDate(),r.startTime(),r.endTime(),r.origin(),r.destination(),r.routeNotes(),r.useClassification(),r.purpose(),r.passengerLoadNotes(),r.startOdometer(),r.endOdometer(),r.declarationAccepted(),r.evidenceId(),actors.resolve(h),actors.resolveSourceChannel(h)));return ResponseEntity.created(URI.create("/api/v1/fuel/logbooks/"+l.id())).body(ApiResponse.ok(l));}

    /**
     * Paged, filtered logbook register.
     *
     * <p>A {@code FLEET_DRIVER}-only actor still sees their own records and nothing else; that
     * restriction is applied in the query, not by the caller.
     */
    @GetMapping public ApiResponse<FuelPageResponse<DriverLogbook>> list(
            @RequestParam String siteCode,
            @RequestParam(required=false)DriverLogbook.Status status,
            @RequestParam(required=false)UUID driverId,
            @RequestParam(required=false)UUID vehicleId,
            @RequestParam(required=false)DriverLogbook.UseClassification useClassification,
            @RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate journeyFrom,
            @RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate journeyTo,
            @RequestParam(defaultValue="0")int page,
            @RequestParam(defaultValue="25")int size,
            @RequestParam(required=false)String sort,
            HttpServletRequest h){
        return ApiResponse.ok(FuelPageResponse.of(service.logbooks(siteCode,status,driverId,vehicleId,
                useClassification,journeyFrom,journeyTo,FuelPageResponse.paging(page,size,sort),actors.resolve(h))));
    }

    @GetMapping("/{id}") public ApiResponse<DriverLogbook> detail(@PathVariable UUID id,HttpServletRequest h){return ApiResponse.ok(service.logbook(id,actors.resolve(h)));}

    /**
     * The logbook's transition history.
     *
     * <p>Every transition was already written to the audit log with a before and after image; this
     * is the read that was missing, so a detail screen can show the route from draft to approval
     * rather than reconstructing it from the record's own timestamps.
     */
    @GetMapping("/{id}/history") public ApiResponse<List<AuditEvent>> history(@PathVariable UUID id,HttpServletRequest h){
        return ApiResponse.ok(service.history("DriverLogbook",id,actors.resolve(h)));
    }

    @PostMapping("/{id}/{action:submit|review|return|approve|reopen|cancel}") public ApiResponse<DriverLogbook> transition(@PathVariable UUID id,@PathVariable String action,@RequestBody(required=false)TransitionRequest r,HttpServletRequest h){return ApiResponse.ok(service.transitionLogbook(id,action,r==null?null:r.comment(),actors.resolve(h),actors.resolveSourceChannel(h)));}

    public record LogbookRequest(@NotBlank String siteCode,@NotNull UUID driverId,@NotNull UUID vehicleId,UUID tripId,@NotNull LocalDate journeyDate,@NotNull Instant startTime,Instant endTime,@NotBlank String origin,@NotBlank String destination,String routeNotes,@NotNull DriverLogbook.UseClassification useClassification,@NotBlank String purpose,String passengerLoadNotes,@PositiveOrZero long startOdometer,Long endOdometer,boolean declarationAccepted,UUID evidenceId){}
    public record TransitionRequest(String comment){}
}
