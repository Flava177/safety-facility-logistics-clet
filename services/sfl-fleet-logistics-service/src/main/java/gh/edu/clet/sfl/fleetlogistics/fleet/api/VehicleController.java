package gh.edu.clet.sfl.fleetlogistics.fleet.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.mapper.FleetAssessmentMapper;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.mapper.FleetWorkflowMapper;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.mapper.VehicleResponseMapper;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.request.ChangeVehicleLifecycleRequest;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.request.CorrectOdometerRequest;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.request.RecordVehicleServiceRequest;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.request.RegisterComplianceDocumentRequest;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.request.RegisterVehicleRequest;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.request.UpdateVehicleRequest;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.ComplianceDocumentResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.PageResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.ServiceHistoryResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.ServiceRecordResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.VehicleResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.ChangeVehicleLifecycleCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.CorrectOdometerCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.RecordVehicleServiceCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.RegisterComplianceDocumentCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.request.FleetTripRequests;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.FleetIntegrationResponses.VehicleLocationResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.FleetWorkflowResponses.InspectionResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentType;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.ReadinessResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.RecordInspectionCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.RegisterVehicleCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.UpdateVehicleCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.query.VehicleQueryService;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.TripApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.VehicleApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleInspection;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Vehicle;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleAvailabilityStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleCategory;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleLifecycleStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleServiceStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vehicle register endpoints (SRS-SFL-S166-01).
 *
 * <p>The controller does no business work: it resolves the actor, binds and validates the request,
 * delegates to an application service and shapes the response. Authorisation, invariants, audit and
 * event publication all live behind the application boundary.
 */
@RestController
@RequestMapping("/api/v1/fleet/vehicles")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Vehicles")
class VehicleController {

    private final VehicleApplicationService vehicleService;
    private final VehicleQueryService vehicleQueries;
    private final VehicleResponseMapper mapper;
    private final FleetAssessmentMapper assessmentMapper;
    private final FleetWorkflowMapper workflowMapper;
    private final TripApplicationService tripService;
    private final FleetActorResolver actorResolver;
    private final Clock clock;

    VehicleController(VehicleApplicationService vehicleService, VehicleQueryService vehicleQueries,
            VehicleResponseMapper mapper, FleetAssessmentMapper assessmentMapper,
            FleetWorkflowMapper workflowMapper, TripApplicationService tripService,
            FleetActorResolver actorResolver, Clock clock) {
        this.vehicleService = vehicleService;
        this.vehicleQueries = vehicleQueries;
        this.mapper = mapper;
        this.assessmentMapper = assessmentMapper;
        this.workflowMapper = workflowMapper;
        // A standalone inspection is the same operation as a trip inspection with no trip, so it goes
        // through the same service rather than a second implementation of the same rules.
        this.tripService = tripService;
        this.actorResolver = actorResolver;
        this.clock = clock;
    }

    @PostMapping
    ResponseEntity<ApiResponse<VehicleResponse>> register(@Valid @RequestBody RegisterVehicleRequest request,
            HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        SourceChannel channel = actorResolver.resolveSourceChannel(httpRequest);

        Vehicle vehicle = vehicleService.register(new RegisterVehicleCommand(
                request.registrationNumber(), request.vin(), request.make(), request.model(),
                request.manufactureYear(), request.category(), request.capacity(), request.siteCode(),
                request.responsibleUnit(), request.operationalOwner(), request.acquisitionReference(),
                request.initialOdometer(), request.emergencyOnlyOrDefault(), request.allowedOperatingModes(),
                actor, channel, actorResolver.resolveIdempotencyKey(httpRequest)));

        return ResponseEntity
                .created(URI.create("/api/v1/fleet/vehicles/" + vehicle.id()))
                .body(ApiResponse.ok(mapper.toResponse(vehicle, vehicleQueries.canReadSensitive(actor))));
    }

    @GetMapping
    ApiResponse<PageResponse<VehicleResponse>> search(
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) VehicleLifecycleStatus status,
            @RequestParam(required = false) VehicleServiceStatus serviceStatus,
            @RequestParam(required = false) VehicleAvailabilityStatus availability,
            @RequestParam(required = false) VehicleCategory category,
            @RequestParam(required = false) String responsibleUnit,
            @RequestParam(required = false) String registrationNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        boolean sensitive = vehicleQueries.canReadSensitive(actor);

        VehicleRepository.VehiclePage result = vehicleQueries.search(
                new VehicleRepository.VehicleSearchCriteria(siteCode, status, serviceStatus, availability,
                        category, responsibleUnit, registrationNumber, page, size, sort),
                actor);

        return ApiResponse.ok(new PageResponse<>(
                result.content().stream().map(vehicle -> mapper.toResponse(vehicle, sensitive)).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages(),
                result.page() == 0, result.page() >= result.totalPages() - 1, result.sort()));
    }

    @GetMapping("/{vehicleId}")
    ApiResponse<VehicleResponse> findById(@PathVariable UUID vehicleId, HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        Vehicle vehicle = vehicleQueries.findById(vehicleId, actor);
        return ApiResponse.ok(mapper.toResponse(vehicle, vehicleQueries.canReadSensitive(actor)));
    }

    @PatchMapping("/{vehicleId}")
    ApiResponse<VehicleResponse> update(@PathVariable UUID vehicleId,
            @Valid @RequestBody UpdateVehicleRequest request, HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        Vehicle vehicle = vehicleService.update(new UpdateVehicleCommand(vehicleId, request.vin(), request.make(),
                request.model(), request.manufactureYear(), request.category(), request.capacity(),
                request.responsibleUnit(), request.operationalOwner(), request.acquisitionReference(),
                request.emergencyOnlyOrDefault(), request.allowedOperatingModes(), request.expectedVersion(),
                actor, actorResolver.resolveSourceChannel(httpRequest)));
        return ApiResponse.ok(mapper.toResponse(vehicle, vehicleQueries.canReadSensitive(actor)));
    }

    @PatchMapping("/{vehicleId}/lifecycle")
    ApiResponse<VehicleResponse> changeLifecycle(@PathVariable UUID vehicleId,
            @Valid @RequestBody ChangeVehicleLifecycleRequest request, HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        Vehicle vehicle = vehicleService.changeLifecycle(new ChangeVehicleLifecycleCommand(vehicleId,
                request.targetStatus(), request.reason(), request.expectedVersion(), actor,
                actorResolver.resolveSourceChannel(httpRequest)));
        return ApiResponse.ok(mapper.toResponse(vehicle, vehicleQueries.canReadSensitive(actor)));
    }

    @PostMapping("/{vehicleId}/compliance-documents")
    ResponseEntity<ApiResponse<ComplianceDocumentResponse>> registerComplianceDocument(
            @PathVariable UUID vehicleId, @Valid @RequestBody RegisterComplianceDocumentRequest request,
            HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        var document = vehicleService.registerComplianceDocument(new RegisterComplianceDocumentCommand(
                vehicleId, request.documentType(), request.documentReference(), request.issuingAuthority(),
                request.issuedOn(), request.expiresOn(), request.evidenceId(), request.retentionClass(),
                actor, actorResolver.resolveSourceChannel(httpRequest),
                actorResolver.resolveIdempotencyKey(httpRequest)));

        return ResponseEntity
                .created(URI.create("/api/v1/fleet/vehicles/" + vehicleId + "/compliance-documents/"
                        + document.id()))
                .body(ApiResponse.ok(mapper.toResponse(document, clock.instant())));
    }

    /**
      * The vehicle's readiness, on its own terms.
      *
      * <p>Closes gap 2. The same policy {@code trips/assignment-preview} runs, reached without
      * having to pretend a trip is involved.
      */
    @GetMapping("/{vehicleId}/readiness")
    ApiResponse<ReadinessResponse> readiness(@PathVariable UUID vehicleId, HttpServletRequest httpRequest) {
        return ApiResponse.ok(assessmentMapper
                .toResponse(vehicleService.readiness(vehicleId, actorResolver.resolve(httpRequest))));
    }

    /**
      * The vehicle's movement history, newest first.
      *
      * <p>Closes gap 3. These snapshots have been written on every telematics callback since the
      * service was built and only the latest one was readable, so a vehicle screen could say where a
      * vehicle is and never where it had been. Freshness is the reader's judgement to make from
      * {@code recordedAt}: this is a vendor projection, and how stale is too stale depends on the
      * question being asked.
      */
    @GetMapping("/{vehicleId}/movement")
    ApiResponse<List<VehicleLocationResponse>> movement(@PathVariable UUID vehicleId,
            @RequestParam(defaultValue = "50") int size, HttpServletRequest httpRequest) {
        return ApiResponse.ok(vehicleService.movementHistory(vehicleId, size, actorResolver.resolve(httpRequest))
                .stream()
                .map(VehicleLocationResponse::from)
                .toList());
    }

    /**
      * Records a standalone (periodic) inspection against a vehicle with no open trip.
      *
      * <p>Closes gap 4, which blocked the periodic-inspection half of SRS-SFL-S166-01: inspections
      * could only be recorded against a trip, so a vehicle sitting in the yard could not be inspected
      * at all. The request record and the service path both existed already —
      * {@code TripApplicationService.recordInspection} accepts a null trip with an explicit vehicle —
      * and nothing mapped it.
      */
    @PostMapping("/{vehicleId}/inspections")
    ResponseEntity<ApiResponse<InspectionResponse>> recordInspection(@PathVariable UUID vehicleId,
            @Valid @RequestBody FleetTripRequests.RecordStandaloneInspection request,
            HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        List<RecordInspectionCommand.Finding> findings = request.findings() == null
                ? List.of()
                : request.findings().stream()
                        .map(finding -> new RecordInspectionCommand.Finding(finding.checkCode(),
                                finding.description(), finding.severity()))
                        .toList();

        // tripId is null and vehicleId explicit: recordInspection has always accepted that pair, and
        // nothing mapped it, which is what left a vehicle in the yard uninspectable.
        VehicleInspection inspection = tripService.recordInspection(new RecordInspectionCommand(null, vehicleId,
                request.inspectionType(), request.odometerReading(), request.evidenceId(), findings,
                request.notes(), actor, actorResolver.resolveSourceChannel(httpRequest),
                actorResolver.resolveIdempotencyKey(httpRequest)));

        return ResponseEntity
                .created(URI.create("/api/v1/fleet/vehicles/" + vehicleId + "/inspections/" + inspection.id()))
                .body(ApiResponse.ok(workflowMapper.toResponse(inspection)));
    }

    /**
      * Cross-fleet compliance search.
      *
      * <p>Closes gap 10. Compliance was reachable per vehicle only, so the screen fanned out over the
      * first fifty active vehicles in scope and said so on the page — correct for a small fleet and
      * quietly wrong for any other. Mapped under {@code /vehicles} rather than at the root because
      * a compliance document belongs to a vehicle.
      */
    @GetMapping("/compliance-documents")
    ApiResponse<List<ComplianceDocumentResponse>> searchComplianceDocuments(
            @RequestParam(required = false) ComplianceDocumentType documentType,
            @RequestParam(required = false) ComplianceDocumentStatus status,
            @RequestParam(required = false) LocalDate expiringBefore,
            @RequestParam(defaultValue = "200") int size, HttpServletRequest httpRequest) {
        Instant now = clock.instant();
        return ApiResponse.ok(vehicleService
                .searchComplianceDocuments(documentType, status, expiringBefore, size,
                        actorResolver.resolve(httpRequest))
                .stream()
                .map(document -> mapper.toResponse(document, now))
                .toList());
    }

    @GetMapping("/{vehicleId}/compliance-documents")
    ApiResponse<List<ComplianceDocumentResponse>> complianceDocuments(@PathVariable UUID vehicleId,
            HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        return ApiResponse.ok(vehicleQueries.findComplianceDocuments(vehicleId, actor).stream()
                .map(document -> mapper.toResponse(document, clock.instant()))
                .toList());
    }

    @PostMapping("/{vehicleId}/service-records")
    ResponseEntity<ApiResponse<ServiceRecordResponse>> recordService(@PathVariable UUID vehicleId,
            @Valid @RequestBody RecordVehicleServiceRequest request, HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        var record = vehicleService.recordService(new RecordVehicleServiceCommand(vehicleId,
                request.serviceType(), request.performedOn(), request.odometerAtService(), request.nextDueOn(),
                request.nextDueOdometer(), request.providerReference(), request.workSummary(), request.outcome(),
                request.evidenceId(), actor, actorResolver.resolveSourceChannel(httpRequest),
                actorResolver.resolveIdempotencyKey(httpRequest)));

        return ResponseEntity
                .created(URI.create("/api/v1/fleet/vehicles/" + vehicleId + "/service-records/" + record.id()))
                .body(ApiResponse.ok(mapper.toResponse(record)));
    }

    @GetMapping("/{vehicleId}/service-history")
    ApiResponse<ServiceHistoryResponse> serviceHistory(@PathVariable UUID vehicleId,
            HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        Vehicle vehicle = vehicleQueries.findById(vehicleId, actor);
        return ApiResponse.ok(mapper.toServiceHistory(vehicle,
                vehicleQueries.findServiceHistory(vehicleId, actor)));
    }

    @PostMapping("/{vehicleId}/odometer-corrections")
    ApiResponse<VehicleResponse> correctOdometer(@PathVariable UUID vehicleId,
            @Valid @RequestBody CorrectOdometerRequest request, HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        Vehicle vehicle = vehicleService.correctOdometer(new CorrectOdometerCommand(vehicleId,
                request.correctedReading(), request.reason(), request.evidenceId(), request.expectedVersion(),
                actor, actorResolver.resolveSourceChannel(httpRequest)));
        return ApiResponse.ok(mapper.toResponse(vehicle, vehicleQueries.canReadSensitive(actor)));
    }
}
