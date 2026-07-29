package gh.edu.clet.sfl.fleetlogistics.fleet.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.ChangeVehicleLifecycleCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.CorrectOdometerCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.RecordVehicleServiceCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.RegisterComplianceDocumentCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.RegisterVehicleCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.UpdateVehicleCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.ComplianceDocumentRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IdempotencyPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IntegrationEventPublisher;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.RuntimeConfigurationPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleLocationRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleServiceRecordRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.event.FleetEventType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.DuplicateActiveIdentifierException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.OptimisticLockConflictException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RecordNotFoundException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocument;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OdometerReading;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OdometerSource;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RegistrationNumber;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RestrictedUse;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleLocationSnapshot;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ReadinessAssessment;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Vehicle;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleIdentificationNumber;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleLifecycleStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleServiceRecord;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleServiceStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy.VehicleLifecyclePolicy;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write use cases for the vehicle register (SRS-SFL-S166-01).
 *
 * <p>Every method here is one transaction that atomically persists three things: the domain state
 * change, the audit record and the outbox event. That is the S166-01 workflow — "System saves the
 * record, writes audit evidence and publishes any required change event" — and doing it in one
 * transaction is what stops an event escaping for a change that rolled back.
 */
@Service
public class VehicleApplicationService {

    private static final String RESOURCE_TYPE = "Vehicle";
    private static final String COMPLIANCE_RESOURCE_TYPE = "ComplianceDocument";
    private static final String SERVICE_RESOURCE_TYPE = "VehicleServiceRecord";

    private final VehicleRepository vehicles;
    private final ComplianceDocumentRepository complianceDocuments;
    private final VehicleServiceRecordRepository serviceRecords;
    private final VehicleLocationRepository vehicleLocations;
    private final FleetReadinessService readinessService;
    private final FleetAccessPolicy accessPolicy;
    private final AuditPort auditPort;
    private final IntegrationEventPublisher eventPublisher;
    private final IdempotencyPort idempotency;
    private final RuntimeConfigurationPort runtimeConfiguration;
    private final Clock clock;

    public VehicleApplicationService(VehicleRepository vehicles, ComplianceDocumentRepository complianceDocuments,
            VehicleServiceRecordRepository serviceRecords, VehicleLocationRepository vehicleLocations,
            FleetReadinessService readinessService, FleetAccessPolicy accessPolicy, AuditPort auditPort,
            IntegrationEventPublisher eventPublisher, IdempotencyPort idempotency,
            RuntimeConfigurationPort runtimeConfiguration, Clock clock) {
        this.vehicles = vehicles;
        this.complianceDocuments = complianceDocuments;
        this.serviceRecords = serviceRecords;
        this.vehicleLocations = vehicleLocations;
        this.readinessService = readinessService;
        this.accessPolicy = accessPolicy;
        this.auditPort = auditPort;
        this.eventPublisher = eventPublisher;
        this.idempotency = idempotency;
        this.runtimeConfiguration = runtimeConfiguration;
        this.clock = clock;
    }

    /** SRS-SFL-S166-01: register a vehicle. */
    @Transactional
    public Vehicle register(RegisterVehicleCommand command) {
        SiteCode site = SiteCode.of(command.siteCode());
        accessPolicy.require(command.actor(), SflPermission.FLEET_VEHICLE_MANAGE, site, RESOURCE_TYPE, null);

        String fingerprint = idempotency.fingerprint(command.idempotencyPayload());
        Optional<UUID> replayed = idempotency.findExistingResult("register-vehicle", command.idempotencyKey(),
                fingerprint);
        if (replayed.isPresent()) {
            return vehicles.findById(replayed.get())
                    .orElseThrow(() -> RecordNotFoundException.of(RESOURCE_TYPE, replayed.get()));
        }

        RegistrationNumber registration = RegistrationNumber.of(command.registrationNumber());
        requireUniqueActiveRegistration(site, registration);
        VehicleIdentificationNumber vin = VehicleIdentificationNumber.ofNullable(command.vin());
        requireUniqueActiveVin(site, vin);

        Instant now = clock.instant();
        Vehicle vehicle = Vehicle.register(
                UUID.randomUUID(),
                registration,
                vin,
                new gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleSpecification(command.make(),
                        command.model(), command.manufactureYear(), command.category(), command.capacity()),
                site,
                command.responsibleUnit(),
                command.operationalOwner(),
                command.acquisitionReference(),
                OdometerReading.of(command.initialOdometer(), OdometerSource.MANUAL_ENTRY, now),
                restrictedUseOf(command.emergencyOnly(), command.allowedOperatingModes()),
                RecordMetadata.createdBy(command.actor().actorId(), now, command.sourceChannel(),
                        command.actor().correlationId()));

        Vehicle saved = vehicles.save(vehicle);
        auditPort.record(command.actor(), command.sourceChannel(), site, AuditAction.CREATE, RESOURCE_TYPE,
                saved.id().toString(), null, auditImage(saved));
        eventPublisher.publish(FleetEventType.VEHICLE_CREATED, RESOURCE_TYPE, saved.id().toString(), site,
                command.actor(), vehicleCreatedPayload(saved));
        idempotency.recordResult("register-vehicle", command.idempotencyKey(), fingerprint, saved.id(),
                site.value(), command.actor().actorId());
        return saved;
    }

    /** SRS-SFL-S166-01: update the descriptive attributes of a vehicle. */
    @Transactional
    public Vehicle update(UpdateVehicleCommand command) {
        Vehicle existing = requireVehicle(command.vehicleId());
        accessPolicy.require(command.actor(), SflPermission.FLEET_VEHICLE_MANAGE, existing.siteCode(),
                RESOURCE_TYPE, existing.id().toString());
        requireExpectedVersion(existing, command.expectedVersion());
        VehicleLifecyclePolicy.requireEditable(existing.lifecycleStatus());

        VehicleIdentificationNumber vin = VehicleIdentificationNumber.ofNullable(command.vin());
        if (vin != null && !vin.equals(existing.vin())) {
            requireUniqueActiveVin(existing.siteCode(), vin);
        }

        Instant now = clock.instant();
        Vehicle updated = existing.updateDetails(
                vin,
                new gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleSpecification(command.make(),
                        command.model(), command.manufactureYear(), command.category(), command.capacity()),
                command.responsibleUnit(),
                command.operationalOwner(),
                command.acquisitionReference(),
                restrictedUseOf(command.emergencyOnly(), command.allowedOperatingModes()),
                existing.metadata().modifiedBy(command.actor().actorId(), now, command.sourceChannel(),
                        command.actor().correlationId()));

        Vehicle saved = vehicles.save(updated);
        auditPort.record(command.actor(), command.sourceChannel(), saved.siteCode(), AuditAction.UPDATE,
                RESOURCE_TYPE, saved.id().toString(), auditImage(existing), auditImage(saved));
        eventPublisher.publish(FleetEventType.VEHICLE_UPDATED, RESOURCE_TYPE, saved.id().toString(),
                saved.siteCode(), command.actor(), Map.of(
                        "vehicleId", saved.id().toString(),
                        "registrationNumber", saved.registrationNumber().value(),
                        "version", saved.metadata().version()));
        return saved;
    }

    /** SRS-SFL-S166-01: apply a lifecycle transition. */
    @Transactional
    public Vehicle changeLifecycle(ChangeVehicleLifecycleCommand command) {
        Vehicle existing = requireVehicle(command.vehicleId());
        SflPermission required = VehicleLifecyclePolicy.isPrivileged(existing.lifecycleStatus(),
                command.targetStatus())
                ? privilegedLifecyclePermission(existing.lifecycleStatus(), command.targetStatus())
                : SflPermission.FLEET_VEHICLE_LIFECYCLE_MANAGE;
        accessPolicy.require(command.actor(), required, existing.siteCode(), RESOURCE_TYPE,
                existing.id().toString());
        requireExpectedVersion(existing, command.expectedVersion());
        requireReason(command.reason());

        Instant now = clock.instant();
        Vehicle changed = existing.changeLifecycle(command.targetStatus(),
                existing.metadata().modifiedBy(command.actor().actorId(), now, command.sourceChannel(),
                        command.actor().correlationId()));

        Vehicle saved = vehicles.save(changed);
        Map<String, Object> after = new LinkedHashMap<>(auditImage(saved));
        after.put("reason", command.reason());
        auditPort.record(command.actor(), command.sourceChannel(), saved.siteCode(), AuditAction.STATE_TRANSITION,
                RESOURCE_TYPE, saved.id().toString(), auditImage(existing), after);
        eventPublisher.publish(FleetEventType.VEHICLE_LIFECYCLE_CHANGED, RESOURCE_TYPE, saved.id().toString(),
                saved.siteCode(), command.actor(), Map.of(
                        "vehicleId", saved.id().toString(),
                        "fromStatus", existing.lifecycleStatus().name(),
                        "toStatus", saved.lifecycleStatus().name(),
                        "reason", command.reason()));
        if (existing.availabilityStatus() != saved.availabilityStatus()) {
            publishAvailabilityChanged(command.actor(), existing, saved, "LIFECYCLE_CHANGE");
        }
        return saved;
    }

    /**
     * One vehicle's readiness, on its own terms.
     *
     * <p>Closes gap 2. The only readiness answer available was {@code trips/assignment-preview},
     * which runs the same policy but reads oddly on a vehicle screen and demands a trip shape for a
     * question that has nothing to do with a trip. Same policy, same answer, vehicle-centric door.
     */
    @Transactional(readOnly = true)
    public ReadinessAssessment readiness(UUID vehicleId, ActorContext actor) {
        Vehicle vehicle = vehicles.findById(vehicleId)
                .orElseThrow(() -> RecordNotFoundException.of(RESOURCE_TYPE, vehicleId));
        accessPolicy.require(actor, SflPermission.FLEET_VEHICLE_READ, vehicle.siteCode(), RESOURCE_TYPE,
                vehicleId.toString());
        return readinessService.assessVehicle(vehicle);
    }

    /**
     * One vehicle's movement history, newest first.
     *
     * <p>Closes gap 3. The snapshots were written on every telematics callback and only the latest
     * was readable, so the vehicle screen could say where a vehicle is and never where it had been.
     *
     * <p>The freshness of each snapshot is the operator's own judgement to make from
     * {@code recordedAt}: this is a vendor projection, and how stale is too stale depends on what the
     * question is.
     */
    @Transactional(readOnly = true)
    public List<VehicleLocationSnapshot> movementHistory(UUID vehicleId, int limit, ActorContext actor) {
        Vehicle vehicle = vehicles.findById(vehicleId)
                .orElseThrow(() -> RecordNotFoundException.of(RESOURCE_TYPE, vehicleId));
        accessPolicy.require(actor, SflPermission.FLEET_VEHICLE_READ, vehicle.siteCode(), RESOURCE_TYPE,
                vehicleId.toString());
        return vehicleLocations.findByVehicle(vehicleId, limit);
    }

    /**
     * Cross-fleet compliance search.
     *
     * <p>Closes gap 10. The compliance screen fanned out over the first fifty active vehicles in
     * scope and said so on the page — correct for a small fleet and quietly wrong for any other.
     */
    @Transactional(readOnly = true)
    public List<ComplianceDocument> searchComplianceDocuments(ComplianceDocumentType documentType,
            ComplianceDocumentStatus status, LocalDate expiringBefore, int limit, ActorContext actor) {
        accessPolicy.requirePermission(actor, SflPermission.FLEET_VEHICLE_READ, "ComplianceDocument");
        return complianceDocuments.search(accessPolicy.requireSiteScopeFilter(actor), documentType, status,
                expiringBefore, limit);
    }

    /** SRS-SFL-S166-01: register a compliance document, superseding any current one of the same type. */
    @Transactional
    public ComplianceDocument registerComplianceDocument(RegisterComplianceDocumentCommand command) {
        Vehicle vehicle = requireVehicle(command.vehicleId());
        accessPolicy.require(command.actor(), SflPermission.FLEET_COMPLIANCE_MANAGE, vehicle.siteCode(),
                COMPLIANCE_RESOURCE_TYPE, vehicle.id().toString());
        VehicleLifecyclePolicy.requireEditable(vehicle.lifecycleStatus());

        String fingerprint = idempotency.fingerprint(command.idempotencyPayload());
        Optional<UUID> replayed = idempotency.findExistingResult("register-compliance-document",
                command.idempotencyKey(), fingerprint);
        if (replayed.isPresent()) {
            return complianceDocuments.findById(replayed.get())
                    .orElseThrow(() -> RecordNotFoundException.of(COMPLIANCE_RESOURCE_TYPE, replayed.get()));
        }

        Instant now = clock.instant();
        RecordMetadata metadata = RecordMetadata.createdBy(command.actor().actorId(), now, command.sourceChannel(),
                command.actor().correlationId());

        complianceDocuments.findCurrentByVehicleAndType(vehicle.id(), command.documentType())
                .ifPresent(current -> {
                    ComplianceDocument superseded = current.supersede(
                            current.metadata().modifiedBy(command.actor().actorId(), now, command.sourceChannel(),
                                    command.actor().correlationId()));
                    complianceDocuments.save(superseded);
                    auditPort.record(command.actor(), command.sourceChannel(), vehicle.siteCode(),
                            AuditAction.STATE_TRANSITION, COMPLIANCE_RESOURCE_TYPE, current.id().toString(),
                            complianceImage(current), complianceImage(superseded));
                });

        ComplianceDocument document = ComplianceDocument.register(UUID.randomUUID(), vehicle.id(),
                vehicle.siteCode(), command.documentType(), command.documentReference(), command.issuingAuthority(),
                command.issuedOn(), command.expiresOn(), command.evidenceId(), command.retentionClass(), now,
                runtimeConfiguration.complianceExpiryWarningWindow(vehicle.siteCode().value()), metadata);

        ComplianceDocument saved = complianceDocuments.save(document);
        auditPort.record(command.actor(), command.sourceChannel(), vehicle.siteCode(), AuditAction.CREATE,
                COMPLIANCE_RESOURCE_TYPE, saved.id().toString(), null, complianceImage(saved));
        idempotency.recordResult("register-compliance-document", command.idempotencyKey(), fingerprint, saved.id(),
                vehicle.siteCode().value(), command.actor().actorId());
        return saved;
    }

    /** SRS-SFL-S166-01: record a service event and recompute the vehicle's service status. */
    @Transactional
    public VehicleServiceRecord recordService(RecordVehicleServiceCommand command) {
        Vehicle vehicle = requireVehicle(command.vehicleId());
        accessPolicy.require(command.actor(), SflPermission.FLEET_SERVICE_RECORD_MANAGE, vehicle.siteCode(),
                SERVICE_RESOURCE_TYPE, vehicle.id().toString());
        VehicleLifecyclePolicy.requireEditable(vehicle.lifecycleStatus());

        String fingerprint = idempotency.fingerprint(command.idempotencyPayload());
        Optional<UUID> replayed = idempotency.findExistingResult("record-vehicle-service", command.idempotencyKey(),
                fingerprint);
        if (replayed.isPresent()) {
            return serviceRecords.findById(replayed.get())
                    .orElseThrow(() -> RecordNotFoundException.of(SERVICE_RESOURCE_TYPE, replayed.get()));
        }

        Instant now = clock.instant();
        RecordMetadata metadata = RecordMetadata.createdBy(command.actor().actorId(), now, command.sourceChannel(),
                command.actor().correlationId());
        VehicleServiceRecord record = VehicleServiceRecord.record(UUID.randomUUID(), vehicle.id(),
                vehicle.siteCode(), command.serviceType(), command.performedOn(), command.odometerAtService(),
                command.nextDueOn(), command.nextDueOdometer(), command.providerReference(), command.workSummary(),
                command.outcome(), command.evidenceId(), metadata);
        VehicleServiceRecord saved = serviceRecords.save(record);

        // The service reading advances the vehicle odometer; a lower reading is rejected by the domain.
        Vehicle withOdometer = vehicle.recordOdometer(command.odometerAtService(), OdometerSource.SERVICE_RECORD,
                now, vehicle.metadata().modifiedBy(command.actor().actorId(), now, command.sourceChannel(),
                        command.actor().correlationId()));
        VehicleServiceStatus status = saved.deriveStatus(now, withOdometer.odometer().value(),
                runtimeConfiguration.serviceDueWarningWindow(vehicle.siteCode().value()));
        Vehicle updatedVehicle = vehicles.save(withOdometer.withServiceStatus(status, withOdometer.metadata()));

        auditPort.record(command.actor(), command.sourceChannel(), vehicle.siteCode(), AuditAction.CREATE,
                SERVICE_RESOURCE_TYPE, saved.id().toString(), null, serviceImage(saved));
        auditPort.record(command.actor(), command.sourceChannel(), vehicle.siteCode(), AuditAction.UPDATE,
                RESOURCE_TYPE, vehicle.id().toString(), auditImage(vehicle), auditImage(updatedVehicle));
        publishServiceStatusEvent(command.actor(), vehicle, updatedVehicle, saved);
        if (vehicle.availabilityStatus() != updatedVehicle.availabilityStatus()) {
            publishAvailabilityChanged(command.actor(), vehicle, updatedVehicle, "SERVICE_STATUS_CHANGE");
        }
        idempotency.recordResult("record-vehicle-service", command.idempotencyKey(), fingerprint, saved.id(),
                vehicle.siteCode().value(), command.actor().actorId());
        return saved;
    }

    /** SRS-SFL-S166-01/-03: the authorised odometer-correction workflow. */
    @Transactional
    public Vehicle correctOdometer(CorrectOdometerCommand command) {
        Vehicle existing = requireVehicle(command.vehicleId());
        accessPolicy.require(command.actor(), SflPermission.FLEET_VEHICLE_ODOMETER_CORRECT, existing.siteCode(),
                RESOURCE_TYPE, existing.id().toString());
        requireExpectedVersion(existing, command.expectedVersion());
        requireReason(command.reason());
        if (command.evidenceId() == null) {
            throw new IllegalArgumentException("evidenceId is required for an odometer correction");
        }

        Instant now = clock.instant();
        Vehicle corrected = existing.correctOdometer(command.correctedReading(), now,
                existing.metadata().modifiedBy(command.actor().actorId(), now, command.sourceChannel(),
                        command.actor().correlationId()));
        Vehicle saved = vehicles.save(corrected);

        Map<String, Object> after = new LinkedHashMap<>(auditImage(saved));
        after.put("reason", command.reason());
        after.put("evidenceId", command.evidenceId().toString());
        auditPort.record(command.actor(), command.sourceChannel(), saved.siteCode(),
                AuditAction.ODOMETER_CORRECTION, RESOURCE_TYPE, saved.id().toString(), auditImage(existing), after);
        eventPublisher.publish(FleetEventType.VEHICLE_UPDATED, RESOURCE_TYPE, saved.id().toString(),
                saved.siteCode(), command.actor(), Map.of(
                        "vehicleId", saved.id().toString(),
                        "changedFields", java.util.List.of("odometer"),
                        "previousOdometer", existing.odometer().value(),
                        "correctedOdometer", saved.odometer().value()));
        return saved;
    }

    // --- helpers ------------------------------------------------------------------------

    private Vehicle requireVehicle(UUID vehicleId) {
        return vehicles.findById(vehicleId)
                .orElseThrow(() -> RecordNotFoundException.of(RESOURCE_TYPE, vehicleId));
    }

    private void requireUniqueActiveRegistration(SiteCode site, RegistrationNumber registration) {
        if (vehicles.findActiveByRegistration(site, registration).isPresent()) {
            throw DuplicateActiveIdentifierException.of(RESOURCE_TYPE, "registrationNumber", registration.value(),
                    site.value());
        }
    }

    private void requireUniqueActiveVin(SiteCode site, VehicleIdentificationNumber vin) {
        if (vin != null && vehicles.findActiveByVin(site, vin.value()).isPresent()) {
            throw DuplicateActiveIdentifierException.of(RESOURCE_TYPE, "vin", vin.masked(), site.value());
        }
    }

    /**
     * Fails fast when the client's expected version is stale, so the caller gets the stable conflict
     * error instead of an overwrite or a database-level lock failure surfacing as a 500.
     */
    private static void requireExpectedVersion(Vehicle vehicle, Long expectedVersion) {
        if (expectedVersion != null && expectedVersion != vehicle.metadata().version()) {
            throw new OptimisticLockConflictException(Map.of(
                    "expectedVersion", expectedVersion,
                    "currentVersion", vehicle.metadata().version()));
        }
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
    }

    private static SflPermission privilegedLifecyclePermission(VehicleLifecycleStatus from,
            VehicleLifecycleStatus to) {
        return from == VehicleLifecycleStatus.ARCHIVED
                ? SflPermission.FLEET_VEHICLE_RESTORE
                : SflPermission.FLEET_VEHICLE_LIFECYCLE_MANAGE;
    }

    private static RestrictedUse restrictedUseOf(boolean emergencyOnly,
            java.util.Set<gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode> modes) {
        if (emergencyOnly) {
            return RestrictedUse.forEmergencyUseOnly();
        }
        return modes == null || modes.isEmpty() ? RestrictedUse.unrestricted() : RestrictedUse.limitedTo(modes);
    }

    private void publishAvailabilityChanged(ActorContext actor, Vehicle before, Vehicle after, String cause) {
        eventPublisher.publish(FleetEventType.VEHICLE_AVAILABILITY_CHANGED, RESOURCE_TYPE, after.id().toString(),
                after.siteCode(), actor, Map.of(
                        "vehicleId", after.id().toString(),
                        "fromStatus", before.availabilityStatus().name(),
                        "toStatus", after.availabilityStatus().name(),
                        "cause", cause));
    }

    private void publishServiceStatusEvent(ActorContext actor, Vehicle before, Vehicle after,
            VehicleServiceRecord record) {
        if (before.serviceStatus() == after.serviceStatus()) {
            return;
        }
        FleetEventType eventType = switch (after.serviceStatus()) {
            case DUE -> FleetEventType.VEHICLE_SERVICE_DUE;
            case OVERDUE, OUT_OF_SERVICE -> FleetEventType.VEHICLE_SERVICE_OVERDUE;
            case IN_SERVICE -> null;
        };
        if (eventType == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("vehicleId", after.id().toString());
        payload.put("serviceStatus", after.serviceStatus().name());
        payload.put("nextDueOn", record.nextDueOn() == null ? null : record.nextDueOn().toString());
        payload.put("nextDueOdometer", record.nextDueOdometer());
        payload.put("currentOdometer", after.odometer().value());
        eventPublisher.publish(eventType, RESOURCE_TYPE, after.id().toString(), after.siteCode(), actor, payload);
    }

    /**
     * The audit before/after image. Data-minimised on purpose: it carries the VIN's masked form rather
     * than the value, so the audit log does not become a second copy of the sensitive field.
     */
    static Map<String, Object> auditImage(Vehicle vehicle) {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("vehicleId", vehicle.id().toString());
        image.put("registrationNumber", vehicle.registrationNumber().value());
        image.put("vin", vehicle.vin() == null ? null : vehicle.vin().masked());
        image.put("make", vehicle.specification().make());
        image.put("model", vehicle.specification().model());
        image.put("manufactureYear", vehicle.specification().manufactureYear());
        image.put("category", vehicle.specification().category().name());
        image.put("capacity", vehicle.specification().capacity());
        image.put("siteCode", vehicle.siteCode().value());
        image.put("responsibleUnit", vehicle.responsibleUnit());
        image.put("operationalOwner", vehicle.operationalOwner());
        image.put("lifecycleStatus", vehicle.lifecycleStatus().name());
        image.put("serviceStatus", vehicle.serviceStatus().name());
        image.put("availabilityStatus", vehicle.availabilityStatus().name());
        image.put("odometerValue", vehicle.odometer().value());
        image.put("odometerSource", vehicle.odometer().source().name());
        image.put("emergencyOnly", vehicle.restrictedUse().emergencyOnly());
        image.put("currentTripId", vehicle.currentTripId() == null ? null : vehicle.currentTripId().toString());
        image.put("version", vehicle.metadata().version());
        return image;
    }

    static Map<String, Object> complianceImage(ComplianceDocument document) {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("documentId", document.id().toString());
        image.put("vehicleId", document.vehicleId().toString());
        image.put("documentType", document.documentType().name());
        image.put("documentReference", document.documentReference());
        image.put("issuedOn", document.issuedOn().toString());
        image.put("expiresOn", document.expiresOn().toString());
        image.put("status", document.status().name());
        image.put("retentionClass", document.retentionClass().name());
        image.put("evidenceId", document.evidenceId() == null ? null : document.evidenceId().toString());
        return image;
    }

    static Map<String, Object> serviceImage(VehicleServiceRecord record) {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("serviceRecordId", record.id().toString());
        image.put("vehicleId", record.vehicleId().toString());
        image.put("serviceType", record.serviceType().name());
        image.put("performedOn", record.performedOn().toString());
        image.put("odometerAtService", record.odometerAtService());
        image.put("nextDueOn", record.nextDueOn() == null ? null : record.nextDueOn().toString());
        image.put("nextDueOdometer", record.nextDueOdometer());
        image.put("outcome", record.outcome().name());
        return image;
    }

    private static Map<String, Object> vehicleCreatedPayload(Vehicle vehicle) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("vehicleId", vehicle.id().toString());
        payload.put("registrationNumber", vehicle.registrationNumber().value());
        payload.put("siteCode", vehicle.siteCode().value());
        payload.put("category", vehicle.specification().category().name());
        payload.put("responsibleUnit", vehicle.responsibleUnit());
        payload.put("lifecycleStatus", vehicle.lifecycleStatus().name());
        return payload;
    }
}
