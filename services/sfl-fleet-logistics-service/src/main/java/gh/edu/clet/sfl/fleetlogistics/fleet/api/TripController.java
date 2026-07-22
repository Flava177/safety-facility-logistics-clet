package gh.edu.clet.sfl.fleetlogistics.fleet.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.mapper.FleetAssessmentMapper;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.mapper.FleetWorkflowMapper;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.request.FleetTripRequests;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.FleetWorkflowResponses.InspectionResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.FleetWorkflowResponses.TripResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.PageResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.ReadinessResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.AssignTripCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.CancelTripCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.CloseTripCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.CreateTripCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.HoldTripCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.RecordInspectionCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.StartTripCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.TripRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.query.TripQueryService;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.TripApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Trip;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.TripStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleInspection;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
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

/** Trip and assignment endpoints (SRS-SFL-S166-02). */
@RestController
@RequestMapping("/api/v1/fleet/trips")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Trips")
class TripController {

    private final TripApplicationService tripService;
    private final TripQueryService tripQueries;
    private final FleetWorkflowMapper mapper;
    private final FleetAssessmentMapper assessmentMapper;
    private final FleetActorResolver actorResolver;

    TripController(TripApplicationService tripService, TripQueryService tripQueries, FleetWorkflowMapper mapper,
            FleetAssessmentMapper assessmentMapper, FleetActorResolver actorResolver) {
        this.tripService = tripService;
        this.tripQueries = tripQueries;
        this.mapper = mapper;
        this.assessmentMapper = assessmentMapper;
        this.actorResolver = actorResolver;
    }

    @PostMapping
    ResponseEntity<ApiResponse<TripResponse>> create(@Valid @RequestBody FleetTripRequests.CreateTrip request,
            HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        Trip trip = tripService.create(new CreateTripCommand(request.vehicleId(), request.driverId(),
                request.siteCode(), request.purpose(), request.origin(), request.destination(),
                request.operatingMode(), request.plannedStart(), request.plannedEnd(), actor,
                actorResolver.resolveSourceChannel(httpRequest),
                actorResolver.resolveIdempotencyKey(httpRequest)));

        return ResponseEntity
                .created(URI.create("/api/v1/fleet/trips/" + trip.id()))
                .body(ApiResponse.ok(mapper.toResponse(trip)));
    }

    @GetMapping
    ApiResponse<PageResponse<TripResponse>> search(
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) TripStatus status,
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) UUID driverId,
            @RequestParam(required = false) OperatingMode operatingMode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        TripRepository.TripPage result = tripQueries.search(new TripRepository.TripSearchCriteria(siteCode,
                status, vehicleId, driverId, operatingMode, from, to, page, size, sort), actor);

        return ApiResponse.ok(new PageResponse<>(
                result.content().stream().map(mapper::toResponse).toList(), result.page(), result.size(),
                result.totalElements(), result.totalPages(), result.page() == 0,
                result.page() >= result.totalPages() - 1, result.sort()));
    }

    @GetMapping("/{tripId}")
    ApiResponse<TripResponse> findById(@PathVariable UUID tripId, HttpServletRequest httpRequest) {
        return ApiResponse.ok(mapper.toResponse(
                tripQueries.findById(tripId, actorResolver.resolve(httpRequest))));
    }

    @PatchMapping("/{tripId}/assignment")
    ApiResponse<TripResponse> assign(@PathVariable UUID tripId,
            @Valid @RequestBody FleetTripRequests.AssignTrip request, HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        return ApiResponse.ok(mapper.toResponse(tripService.assign(new AssignTripCommand(tripId,
                request.vehicleId(), request.driverId(), request.reason(), request.expectedVersion(), actor,
                actorResolver.resolveSourceChannel(httpRequest)))));
    }

    @PatchMapping("/{tripId}/start")
    ApiResponse<TripResponse> start(@PathVariable UUID tripId,
            @Valid @RequestBody FleetTripRequests.StartTrip request, HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        return ApiResponse.ok(mapper.toResponse(tripService.start(new StartTripCommand(tripId,
                request.startOdometer(), request.expectedVersion(), actor,
                actorResolver.resolveSourceChannel(httpRequest)))));
    }

    @PatchMapping("/{tripId}/hold")
    ApiResponse<TripResponse> holdOrResume(@PathVariable UUID tripId,
            @Valid @RequestBody FleetTripRequests.HoldTrip request, HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        HoldTripCommand.HoldAction action =
                request.action() == FleetTripRequests.HoldTrip.HoldAction.HOLD
                        ? HoldTripCommand.HoldAction.HOLD
                        : HoldTripCommand.HoldAction.RESUME;
        return ApiResponse.ok(mapper.toResponse(tripService.holdOrResume(new HoldTripCommand(tripId, action,
                request.reason(), request.expectedVersion(), actor,
                actorResolver.resolveSourceChannel(httpRequest)))));
    }

    @PatchMapping("/{tripId}/cancel")
    ApiResponse<TripResponse> cancel(@PathVariable UUID tripId,
            @Valid @RequestBody FleetTripRequests.CancelTrip request, HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        return ApiResponse.ok(mapper.toResponse(tripService.cancel(new CancelTripCommand(tripId,
                request.reason(), request.expectedVersion(), actor,
                actorResolver.resolveSourceChannel(httpRequest)))));
    }

    @PatchMapping("/{tripId}/closure")
    ApiResponse<TripResponse> close(@PathVariable UUID tripId,
            @Valid @RequestBody FleetTripRequests.CloseTrip request, HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        return ApiResponse.ok(mapper.toResponse(tripService.close(new CloseTripCommand(tripId,
                request.closureReason(), request.closureEvidenceId(), request.endOdometer(),
                request.expectedVersion(), actor, actorResolver.resolveSourceChannel(httpRequest)))));
    }

    @PostMapping("/{tripId}/inspections")
    ResponseEntity<ApiResponse<InspectionResponse>> recordInspection(@PathVariable UUID tripId,
            @Valid @RequestBody FleetTripRequests.RecordInspection request, HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        List<RecordInspectionCommand.Finding> findings = request.findings() == null
                ? List.of()
                : request.findings().stream()
                        .map(finding -> new RecordInspectionCommand.Finding(finding.checkCode(),
                                finding.description(), finding.severity()))
                        .toList();

        VehicleInspection inspection = tripService.recordInspection(new RecordInspectionCommand(tripId, null,
                request.inspectionType(), request.odometerReading(), request.evidenceId(), findings,
                request.notes(), actor, actorResolver.resolveSourceChannel(httpRequest),
                actorResolver.resolveIdempotencyKey(httpRequest)));

        return ResponseEntity
                .created(URI.create("/api/v1/fleet/trips/" + tripId + "/inspections/" + inspection.id()))
                .body(ApiResponse.ok(mapper.toResponse(inspection)));
    }

    @GetMapping("/{tripId}/inspections")
    ApiResponse<List<InspectionResponse>> inspections(@PathVariable UUID tripId,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(tripQueries.findInspections(tripId, actorResolver.resolve(httpRequest)).stream()
                .map(mapper::toResponse)
                .toList());
    }

    /**
     * Readiness preview before committing to an assignment.
     *
     * <p>Deliberately the same policy and inputs the assignment itself will use, so the preview and the
     * outcome cannot disagree.
     */
    @GetMapping("/assignment-preview")
    ApiResponse<ReadinessResponse> previewAssignment(
            @RequestParam UUID vehicleId,
            @RequestParam(required = false) UUID driverId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) OperatingMode operatingMode,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(assessmentMapper.toResponse(tripQueries.previewAssignment(vehicleId, driverId,
                from, to, operatingMode, actorResolver.resolve(httpRequest))));
    }
}
