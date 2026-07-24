package gh.edu.clet.sfl.fleetlogistics.fleet.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.mapper.FleetAssessmentMapper;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.request.RegisterDriverRequest;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.request.UpdateDriverRequest;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.DriverResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.EligibilityResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.PageResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.RegisterDriverCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.UpdateDriverCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.DriverProfileRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.query.DriverQueryService;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.DriverApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverEligibilityStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverLifecycleStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverProfileReference;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleCategory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Driver profile reference endpoints (SRS-SFL-S166-01, with eligibility feeding SRS-SFL-S166-05). */
@RestController
@RequestMapping("/api/v1/fleet/drivers")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Drivers")
class DriverController {

    private final DriverApplicationService driverService;
    private final DriverQueryService driverQueries;
    private final FleetAssessmentMapper mapper;
    private final FleetActorResolver actorResolver;
    private final Clock clock;

    DriverController(DriverApplicationService driverService, DriverQueryService driverQueries,
            FleetAssessmentMapper mapper, FleetActorResolver actorResolver, Clock clock) {
        this.driverService = driverService;
        this.driverQueries = driverQueries;
        this.mapper = mapper;
        this.actorResolver = actorResolver;
        this.clock = clock;
    }

    @PostMapping
    ResponseEntity<ApiResponse<DriverResponse>> register(@Valid @RequestBody RegisterDriverRequest request,
            HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        DriverProfileReference driver = driverService.register(new RegisterDriverCommand(
                request.staffReference(), request.displayName(), request.licenceNumber(), request.licenceClass(),
                request.licenceExpiresOn(), request.medicalClearanceExpiresOn(), request.siteCode(),
                request.responsibleUnit(), actor, actorResolver.resolveSourceChannel(httpRequest),
                actorResolver.resolveIdempotencyKey(httpRequest)));

        return ResponseEntity
                .created(URI.create("/api/v1/fleet/drivers/" + driver.id()))
                .body(ApiResponse.ok(mapper.toResponse(driver, driverQueries.canReadSensitive(actor),
                        clock.instant())));
    }

    @GetMapping
    ApiResponse<PageResponse<DriverResponse>> search(
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) DriverLifecycleStatus status,
            @RequestParam(required = false) DriverEligibilityStatus eligibility,
            @RequestParam(required = false) String responsibleUnit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate licenceExpiringBefore,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        boolean sensitive = driverQueries.canReadSensitive(actor);
        Instant now = clock.instant();

        DriverProfileRepository.DriverPage result = driverQueries.search(
                new DriverProfileRepository.DriverSearchCriteria(siteCode, status, eligibility, responsibleUnit,
                        licenceExpiringBefore, search, page, size, sort),
                actor);

        return ApiResponse.ok(new PageResponse<>(
                result.content().stream().map(driver -> mapper.toResponse(driver, sensitive, now)).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages(),
                result.page() == 0, result.page() >= result.totalPages() - 1, result.sort()));
    }

    @GetMapping("/{driverId}")
    ApiResponse<DriverResponse> findById(@PathVariable UUID driverId, HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        DriverProfileReference driver = driverQueries.findById(driverId, actor);
        return ApiResponse.ok(mapper.toResponse(driver, driverQueries.canReadSensitive(actor), clock.instant()));
    }

    @PatchMapping("/{driverId}")
    ApiResponse<DriverResponse> update(@PathVariable UUID driverId,
            @Valid @RequestBody UpdateDriverRequest request, HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        DriverProfileReference driver = driverService.update(new UpdateDriverCommand(driverId,
                request.displayName(), request.licenceNumber(), request.licenceClass(), request.licenceExpiresOn(),
                request.medicalClearanceExpiresOn(), request.responsibleUnit(), request.targetLifecycleStatus(),
                request.lifecycleReason(), request.expectedVersion(), actor,
                actorResolver.resolveSourceChannel(httpRequest)));
        return ApiResponse.ok(mapper.toResponse(driver, driverQueries.canReadSensitive(actor), clock.instant()));
    }

    /**
     * Eligibility for a driver, optionally against a specific vehicle category and period end.
     *
     * <p>Passing {@code until} is what catches a licence that is valid today but lapses mid-trip.
     */
    @GetMapping("/{driverId}/eligibility")
    ApiResponse<EligibilityResponse> eligibility(@PathVariable UUID driverId,
            @RequestParam(required = false) VehicleCategory vehicleCategory,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
            HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        return ApiResponse.ok(mapper.toResponse(
                driverQueries.assessEligibility(driverId, vehicleCategory, until, actor)));
    }
}
