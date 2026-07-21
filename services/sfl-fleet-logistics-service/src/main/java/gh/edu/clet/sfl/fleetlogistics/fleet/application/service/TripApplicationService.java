package gh.edu.clet.sfl.fleetlogistics.fleet.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.AssignTripCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.CancelTripCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.CloseTripCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.CreateTripCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.HoldTripCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.RecordInspectionCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.StartTripCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IdempotencyPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IntegrationEventPublisher;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.TripRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleInspectionRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.workflow.FleetWorkflowRaiser;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.event.FleetEventType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.AssignmentConflictException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.OptimisticLockConflictException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.ReadinessBlockedException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RecordNotFoundException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DateTimeRange;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.InspectionFinding;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OdometerSource;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ReadinessAssessment;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ReadinessBlockerCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Trip;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.TripStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Vehicle;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleInspection;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleServiceStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The vehicle/driver assignment and trip workflow (SRS-SFL-S166-02).
 *
 * <p>Assignment is the operation with the most ways to go wrong, so it is guarded twice: the readiness
 * policy refuses an unfit vehicle or driver with named blockers, and the vehicle row is locked before
 * the overlap check so two dispatchers booking the same vehicle at the same moment serialise instead of
 * both passing. The database exclusion constraint is the third line of defence.
 */
@Service
public class TripApplicationService {

    private static final String RESOURCE_TYPE = "Trip";
    private static final String INSPECTION_RESOURCE_TYPE = "VehicleInspection";

    private final TripRepository trips;
    private final VehicleRepository vehicles;
    private final VehicleInspectionRepository inspections;
    private final FleetReadinessService readinessService;
    private final FleetWorkflowRaiser workflowRaiser;
    private final FleetAccessPolicy accessPolicy;
    private final AuditPort auditPort;
    private final IntegrationEventPublisher eventPublisher;
    private final IdempotencyPort idempotency;
    private final Clock clock;

    public TripApplicationService(TripRepository trips, VehicleRepository vehicles,
            VehicleInspectionRepository inspections, FleetReadinessService readinessService,
            FleetWorkflowRaiser workflowRaiser, FleetAccessPolicy accessPolicy, AuditPort auditPort,
            IntegrationEventPublisher eventPublisher, IdempotencyPort idempotency, Clock clock) {
        this.trips = trips;
        this.vehicles = vehicles;
        this.inspections = inspections;
        this.readinessService = readinessService;
        this.workflowRaiser = workflowRaiser;
        this.accessPolicy = accessPolicy;
        this.auditPort = auditPort;
        this.eventPublisher = eventPublisher;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    /** SRS-SFL-S166-02: create a trip, optionally assigning vehicle and driver immediately. */
    @Transactional
    public Trip create(CreateTripCommand command) {
        SiteCode site = SiteCode.of(command.siteCode());
        accessPolicy.require(command.actor(), SflPermission.FLEET_TRIP_MANAGE, site, RESOURCE_TYPE, null);

        String fingerprint = idempotency.fingerprint(command.idempotencyPayload());
        Optional<UUID> replayed = idempotency.findExistingResult("create-trip", command.idempotencyKey(),
                fingerprint);
        if (replayed.isPresent()) {
            return trips.findById(replayed.get())
                    .orElseThrow(() -> RecordNotFoundException.of(RESOURCE_TYPE, replayed.get()));
        }

        Instant now = clock.instant();
        DateTimeRange period = DateTimeRange.of(command.plannedStart(), command.plannedEnd());
        UUID tripId = UUID.randomUUID();
        RecordMetadata metadata = RecordMetadata.createdBy(command.actor().actorId(), now, command.sourceChannel(),
                command.actor().correlationId());

        Trip trip = Trip.plan(tripId, tripNumber(tripId), site, command.purpose(), command.origin(),
                command.destination(), command.operatingMode(), period, metadata);

        if (command.vehicleId() != null && command.driverId() != null) {
            accessPolicy.requirePermission(command.actor(), SflPermission.FLEET_TRIP_ASSIGN, RESOURCE_TYPE);
            Vehicle vehicle = lockVehicle(command.vehicleId());
            requireReady(readinessService.assessForAssignment(vehicle, command.driverId(), period,
                    command.operatingMode(), site, tripId, false));
            trip = trip.assign(command.vehicleId(), command.driverId(), metadata);
            vehicles.save(vehicle.assignToTrip(tripId, vehicle.metadata().modifiedBy(command.actor().actorId(),
                    now, command.sourceChannel(), command.actor().correlationId())));
        }

        Trip saved = trips.save(trip);
        auditPort.record(command.actor(), command.sourceChannel(), site, AuditAction.CREATE, RESOURCE_TYPE,
                saved.id().toString(), null, auditImage(saved));
        if (saved.status() == TripStatus.ASSIGNED) {
            publishAssigned(command.actor(), saved);
        }
        idempotency.recordResult("create-trip", command.idempotencyKey(), fingerprint, saved.id(), site.value(),
                command.actor().actorId());
        return saved;
    }

    /** SRS-SFL-S166-02: assign or reassign the vehicle and driver. */
    @Transactional
    public Trip assign(AssignTripCommand command) {
        Trip existing = requireTrip(command.tripId());
        accessPolicy.require(command.actor(), SflPermission.FLEET_TRIP_ASSIGN, existing.siteCode(), RESOURCE_TYPE,
                existing.id().toString());
        requireExpectedVersion(existing, command.expectedVersion());

        boolean isReassignment = existing.vehicleId() != null;
        if (isReassignment && (command.reason() == null || command.reason().isBlank())) {
            throw new IllegalArgumentException("A reason is required when reassigning a trip");
        }

        Instant now = clock.instant();
        RecordMetadata metadata = existing.metadata().modifiedBy(command.actor().actorId(), now,
                command.sourceChannel(), command.actor().correlationId());

        Vehicle vehicle = lockVehicle(command.vehicleId());
        requireReady(readinessService.assessForAssignment(vehicle, command.driverId(), existing.plannedPeriod(),
                existing.operatingMode(), existing.siteCode(), existing.id(), false));

        // Release the previously held vehicle before taking the new one, so a reassignment cannot leave
        // two vehicles both believing they are on this trip.
        if (isReassignment && !command.vehicleId().equals(existing.vehicleId())) {
            vehicles.findById(existing.vehicleId()).ifPresent(previous ->
                    vehicles.save(previous.releaseFromTrip(previous.metadata().modifiedBy(
                            command.actor().actorId(), now, command.sourceChannel(),
                            command.actor().correlationId()))));
        }

        Trip assigned = trips.save(existing.assign(command.vehicleId(), command.driverId(), metadata));
        vehicles.save(vehicle.assignToTrip(assigned.id(), vehicle.metadata().modifiedBy(command.actor().actorId(),
                now, command.sourceChannel(), command.actor().correlationId())));

        Map<String, Object> after = new LinkedHashMap<>(auditImage(assigned));
        after.put("reason", command.reason());
        auditPort.record(command.actor(), command.sourceChannel(), assigned.siteCode(),
                isReassignment ? AuditAction.REASSIGN : AuditAction.ASSIGN, RESOURCE_TYPE,
                assigned.id().toString(), auditImage(existing), after);

        if (isReassignment) {
            eventPublisher.publish(FleetEventType.TRIP_REASSIGNED, RESOURCE_TYPE, assigned.id().toString(),
                    assigned.siteCode(), command.actor(), Map.of(
                            "tripId", assigned.id().toString(),
                            "previousVehicleId", String.valueOf(existing.vehicleId()),
                            "previousDriverId", String.valueOf(existing.driverId()),
                            "vehicleId", String.valueOf(assigned.vehicleId()),
                            "driverId", String.valueOf(assigned.driverId()),
                            "reason", String.valueOf(command.reason())));
        } else {
            publishAssigned(command.actor(), assigned);
        }
        return assigned;
    }

    /** SRS-SFL-S166-02: start an assigned trip, gated on a valid pre-trip inspection. */
    @Transactional
    public Trip start(StartTripCommand command) {
        Trip existing = requireTrip(command.tripId());
        accessPolicy.require(command.actor(), SflPermission.FLEET_TRIP_MANAGE, existing.siteCode(), RESOURCE_TYPE,
                existing.id().toString());
        requireExpectedVersion(existing, command.expectedVersion());

        Vehicle vehicle = lockVehicle(existing.vehicleId());
        // inspectionRequired = true: this is the moment the pre-trip check has to exist and pass.
        requireReady(readinessService.assessForAssignment(vehicle, existing.driverId(), existing.plannedPeriod(),
                existing.operatingMode(), existing.siteCode(), existing.id(), true));

        Instant now = clock.instant();
        RecordMetadata metadata = existing.metadata().modifiedBy(command.actor().actorId(), now,
                command.sourceChannel(), command.actor().correlationId());

        Trip started = trips.save(existing.start(now, command.startOdometer(), metadata));
        vehicles.save(vehicle
                .recordOdometer(command.startOdometer(), OdometerSource.MANUAL_ENTRY, now,
                        vehicle.metadata().modifiedBy(command.actor().actorId(), now, command.sourceChannel(),
                                command.actor().correlationId()))
                .markInUse(vehicle.metadata().modifiedBy(command.actor().actorId(), now, command.sourceChannel(),
                        command.actor().correlationId())));

        auditPort.record(command.actor(), command.sourceChannel(), started.siteCode(),
                AuditAction.STATE_TRANSITION, RESOURCE_TYPE, started.id().toString(), auditImage(existing),
                auditImage(started));
        return started;
    }

    /** SRS-SFL-S166-02: hold or resume. */
    @Transactional
    public Trip holdOrResume(HoldTripCommand command) {
        Trip existing = requireTrip(command.tripId());
        accessPolicy.require(command.actor(), SflPermission.FLEET_TRIP_MANAGE, existing.siteCode(), RESOURCE_TYPE,
                existing.id().toString());
        requireExpectedVersion(existing, command.expectedVersion());

        Instant now = clock.instant();
        RecordMetadata metadata = existing.metadata().modifiedBy(command.actor().actorId(), now,
                command.sourceChannel(), command.actor().correlationId());

        Trip updated = command.action() == HoldTripCommand.HoldAction.HOLD
                ? existing.hold(command.reason(), metadata)
                : existing.resume(metadata);
        Trip saved = trips.save(updated);

        auditPort.record(command.actor(), command.sourceChannel(), saved.siteCode(),
                command.action() == HoldTripCommand.HoldAction.HOLD ? AuditAction.HOLD : AuditAction.RESUME,
                RESOURCE_TYPE, saved.id().toString(), auditImage(existing), auditImage(saved));
        return saved;
    }

    /** SRS-SFL-S166-02: cancel. Privileged, and the reason is mandatory. */
    @Transactional
    public Trip cancel(CancelTripCommand command) {
        Trip existing = requireTrip(command.tripId());
        accessPolicy.requirePrivilegedTransition(command.actor(), SflPermission.FLEET_TRIP_CANCEL,
                existing.siteCode(), RESOURCE_TYPE, existing.id().toString());
        requireExpectedVersion(existing, command.expectedVersion());

        Instant now = clock.instant();
        RecordMetadata metadata = existing.metadata().modifiedBy(command.actor().actorId(), now,
                command.sourceChannel(), command.actor().correlationId());

        Trip cancelled = trips.save(existing.cancel(command.reason(), now, metadata));
        releaseVehicle(existing, command.actor(), command.sourceChannel(), now);

        auditPort.record(command.actor(), command.sourceChannel(), cancelled.siteCode(), AuditAction.CANCEL,
                RESOURCE_TYPE, cancelled.id().toString(), auditImage(existing), auditImage(cancelled));
        eventPublisher.publish(FleetEventType.TRIP_CANCELLED, RESOURCE_TYPE, cancelled.id().toString(),
                cancelled.siteCode(), command.actor(), Map.of(
                        "tripId", cancelled.id().toString(),
                        "reason", String.valueOf(command.reason()),
                        "cancelledBy", command.actor().actorId()));
        return cancelled;
    }

    /** SRS-SFL-S166-02: close with the required reason, evidence and end odometer. */
    @Transactional
    public Trip close(CloseTripCommand command) {
        Trip existing = requireTrip(command.tripId());
        accessPolicy.require(command.actor(), SflPermission.FLEET_TRIP_CLOSE, existing.siteCode(), RESOURCE_TYPE,
                existing.id().toString());
        requireExpectedVersion(existing, command.expectedVersion());

        Instant now = clock.instant();
        RecordMetadata metadata = existing.metadata().modifiedBy(command.actor().actorId(), now,
                command.sourceChannel(), command.actor().correlationId());

        Trip closed = trips.save(existing.close(command.closureReason(), command.closureEvidenceId(),
                command.endOdometer(), now, metadata));

        vehicles.findById(existing.vehicleId()).ifPresent(vehicle -> vehicles.save(vehicle
                .recordOdometer(command.endOdometer(), OdometerSource.MANUAL_ENTRY, now,
                        vehicle.metadata().modifiedBy(command.actor().actorId(), now, command.sourceChannel(),
                                command.actor().correlationId()))
                .releaseFromTrip(vehicle.metadata().modifiedBy(command.actor().actorId(), now,
                        command.sourceChannel(), command.actor().correlationId()))));

        auditPort.record(command.actor(), command.sourceChannel(), closed.siteCode(), AuditAction.CLOSE,
                RESOURCE_TYPE, closed.id().toString(), auditImage(existing), auditImage(closed));
        eventPublisher.publish(FleetEventType.TRIP_COMPLETED, RESOURCE_TYPE, closed.id().toString(),
                closed.siteCode(), command.actor(), Map.of(
                        "tripId", closed.id().toString(),
                        "endOdometer", command.endOdometer(),
                        "distanceCovered", String.valueOf(closed.distanceCovered()),
                        "closureReason", String.valueOf(closed.closureReason()),
                        "closureEvidenceId", String.valueOf(closed.closureEvidenceId())));
        return closed;
    }

    /**
     * Records an inspection against a trip.
     *
     * <p>A critical defect does three things at once: it fails the inspection, takes the vehicle out of
     * service, and opens a defect workflow item so somebody owns the rectification.
     */
    @Transactional
    public VehicleInspection recordInspection(RecordInspectionCommand command) {
        Trip trip = command.tripId() == null ? null : requireTrip(command.tripId());
        UUID vehicleId = trip != null ? trip.vehicleId() : command.vehicleId();
        if (vehicleId == null) {
            throw new IllegalArgumentException("An inspection needs either a trip with an assigned vehicle "
                    + "or an explicit vehicleId");
        }
        Vehicle vehicle = vehicles.findById(vehicleId)
                .orElseThrow(() -> RecordNotFoundException.of("Vehicle", vehicleId));

        accessPolicy.require(command.actor(), SflPermission.FLEET_INSPECTION_RECORD, vehicle.siteCode(),
                INSPECTION_RESOURCE_TYPE, vehicleId.toString());
        // A driver may only inspect the vehicle on their own trip.
        if (trip != null && trip.driverId() != null) {
            accessPolicy.requireRecordScope(command.actor(), null, SflPermission.FLEET_TRIP_MANAGE,
                    RESOURCE_TYPE, trip.id().toString());
        }

        String fingerprint = idempotency.fingerprint(command.idempotencyPayload());
        Optional<UUID> replayed = idempotency.findExistingResult("record-inspection", command.idempotencyKey(),
                fingerprint);
        if (replayed.isPresent()) {
            return inspections.findById(replayed.get())
                    .orElseThrow(() -> RecordNotFoundException.of(INSPECTION_RESOURCE_TYPE, replayed.get()));
        }

        Instant now = clock.instant();
        RecordMetadata metadata = RecordMetadata.createdBy(command.actor().actorId(), now, command.sourceChannel(),
                command.actor().correlationId());

        List<InspectionFinding> findings = command.findings() == null
                ? List.of()
                : command.findings().stream()
                        .map(finding -> InspectionFinding.of(finding.checkCode(), finding.description(),
                                finding.severity()))
                        .toList();

        VehicleInspection inspection = inspections.save(VehicleInspection.record(UUID.randomUUID(), vehicleId,
                trip == null ? null : trip.id(), vehicle.siteCode(), command.inspectionType(),
                command.actor().actorId(), now, command.odometerReading(), command.evidenceId(), findings,
                command.notes(), metadata));

        Vehicle updatedVehicle = vehicle.recordOdometer(command.odometerReading(), OdometerSource.INSPECTION, now,
                vehicle.metadata().modifiedBy(command.actor().actorId(), now, command.sourceChannel(),
                        command.actor().correlationId()));
        if (!inspection.permitsUse()) {
            updatedVehicle = updatedVehicle.withServiceStatus(VehicleServiceStatus.OUT_OF_SERVICE,
                    updatedVehicle.metadata());
        }
        vehicles.save(updatedVehicle);

        auditPort.record(command.actor(), command.sourceChannel(), vehicle.siteCode(),
                AuditAction.INSPECTION_RECORDED, INSPECTION_RESOURCE_TYPE, inspection.id().toString(), null,
                inspectionImage(inspection));

        if (!inspection.permitsUse()) {
            eventPublisher.publish(FleetEventType.VEHICLE_INSPECTION_FAILED, INSPECTION_RESOURCE_TYPE,
                    inspection.id().toString(), vehicle.siteCode(), command.actor(), Map.of(
                            "inspectionId", inspection.id().toString(),
                            "vehicleId", vehicleId.toString(),
                            "tripId", trip == null ? "" : trip.id().toString(),
                            "result", inspection.result().name(),
                            "defectCodes", inspection.findings().stream()
                                    .map(InspectionFinding::checkCode).toList()));
            workflowRaiser.raiseInspectionDefect(inspection, updatedVehicle, command.actor(),
                    command.sourceChannel());
        }
        idempotency.recordResult("record-inspection", command.idempotencyKey(), fingerprint, inspection.id(),
                vehicle.siteCode().value(), command.actor().actorId());
        return inspection;
    }

    // --- helpers ------------------------------------------------------------------------

    private Trip requireTrip(UUID tripId) {
        return trips.findById(tripId).orElseThrow(() -> RecordNotFoundException.of(RESOURCE_TYPE, tripId));
    }

    /**
     * Loads the vehicle under a row lock.
     *
     * <p>This is what makes concurrent assignment safe: two dispatchers booking the same vehicle
     * serialise here, so the second one sees the first one's trip when it runs the overlap check.
     */
    private Vehicle lockVehicle(UUID vehicleId) {
        if (vehicleId == null) {
            throw new IllegalArgumentException("vehicleId is required");
        }
        return vehicles.findByIdForUpdate(vehicleId)
                .orElseThrow(() -> RecordNotFoundException.of("Vehicle", vehicleId));
    }

    /**
     * Refuses an assignment the readiness policy blocked.
     *
     * <p>Assignment conflicts are reported as a conflict rather than a validation failure, because the
     * caller's request was well formed — the world simply changed underneath it.
     */
    private static void requireReady(ReadinessAssessment assessment) {
        if (assessment.permitsAssignment()) {
            return;
        }
        List<ReadinessBlockerCode> blocking = assessment.blockingCodes();
        Map<String, Object> details = Map.of(
                "vehicleId", String.valueOf(assessment.vehicleId()),
                "driverId", String.valueOf(assessment.driverId()),
                "blockerCodes", blocking.stream().map(Enum::name).toList(),
                "blockers", assessment.blockers().stream()
                        .map(blocker -> blocker.code().name() + ": " + blocker.message())
                        .toList());

        if (blocking.contains(ReadinessBlockerCode.VEHICLE_ASSIGNMENT_CONFLICT)
                || blocking.contains(ReadinessBlockerCode.DRIVER_ASSIGNMENT_CONFLICT)) {
            throw new AssignmentConflictException(details);
        }
        if (blocking.contains(ReadinessBlockerCode.DRIVER_INELIGIBLE)) {
            throw new gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.DriverIneligibleException(details);
        }
        throw new ReadinessBlockedException(details);
    }

    private void releaseVehicle(Trip trip, ActorContext actor, gh.edu.clet.sfl.fleetlogistics.fleet.domain.model
            .SourceChannel channel, Instant now) {
        if (trip.vehicleId() == null) {
            return;
        }
        vehicles.findById(trip.vehicleId()).ifPresent(vehicle -> vehicles.save(vehicle.releaseFromTrip(
                vehicle.metadata().modifiedBy(actor.actorId(), now, channel, actor.correlationId()))));
    }

    private void publishAssigned(ActorContext actor, Trip trip) {
        eventPublisher.publish(FleetEventType.VEHICLE_ASSIGNED, RESOURCE_TYPE, trip.id().toString(),
                trip.siteCode(), actor, Map.of(
                        "tripId", trip.id().toString(),
                        "tripNumber", trip.tripNumber(),
                        "vehicleId", String.valueOf(trip.vehicleId()),
                        "driverId", String.valueOf(trip.driverId()),
                        "plannedStart", trip.plannedPeriod().start().toString(),
                        "plannedEnd", trip.plannedPeriod().end().toString(),
                        "operatingMode", trip.operatingMode().name()));
    }

    private static void requireExpectedVersion(Trip trip, Long expectedVersion) {
        if (expectedVersion != null && expectedVersion != trip.metadata().version()) {
            throw new OptimisticLockConflictException(Map.of(
                    "expectedVersion", expectedVersion,
                    "currentVersion", trip.metadata().version()));
        }
    }

    private static String tripNumber(UUID id) {
        return "TRP-" + id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    static Map<String, Object> auditImage(Trip trip) {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("tripId", trip.id().toString());
        image.put("tripNumber", trip.tripNumber());
        image.put("vehicleId", trip.vehicleId() == null ? null : trip.vehicleId().toString());
        image.put("driverId", trip.driverId() == null ? null : trip.driverId().toString());
        image.put("siteCode", trip.siteCode().value());
        image.put("operatingMode", trip.operatingMode().name());
        image.put("plannedStart", trip.plannedPeriod().start().toString());
        image.put("plannedEnd", trip.plannedPeriod().end().toString());
        image.put("status", trip.status().name());
        image.put("holdReason", trip.holdReason());
        image.put("cancellationReason", trip.cancellationReason());
        image.put("closureReason", trip.closureReason());
        image.put("closureEvidenceId", trip.closureEvidenceId() == null
                ? null
                : trip.closureEvidenceId().toString());
        image.put("startOdometer", trip.startOdometer());
        image.put("endOdometer", trip.endOdometer());
        image.put("version", trip.metadata().version());
        return image;
    }

    static Map<String, Object> inspectionImage(VehicleInspection inspection) {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("inspectionId", inspection.id().toString());
        image.put("vehicleId", inspection.vehicleId().toString());
        image.put("tripId", inspection.tripId() == null ? null : inspection.tripId().toString());
        image.put("inspectionType", inspection.inspectionType().name());
        image.put("result", inspection.result().name());
        image.put("odometerReading", inspection.odometerReading());
        image.put("evidenceId", inspection.evidenceId() == null ? null : inspection.evidenceId().toString());
        image.put("findings", inspection.findings().stream()
                .map(finding -> finding.checkCode() + "/" + finding.severity())
                .toList());
        return image;
    }

    /** Operating modes a client may request; exposed for the console's assignment form. */
    public static List<OperatingMode> supportedOperatingModes() {
        return List.of(OperatingMode.values());
    }
}
