package gh.edu.clet.sfl.fleetlogistics.fleet.application.workflow;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.ComplianceDocumentRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IntegrationEventPublisher;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.RuntimeConfigurationPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleServiceRecordRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.event.FleetEventType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocument;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Vehicle;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleServiceRecord;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleServiceStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled compliance and service due sweeps for the fleet module.
 *
 * <p>The sweep is deliberately a first-class application service, not scheduler logic, so it can be
 * tested with a controlled clock and invoked manually during readiness rehearsals.
 */
@Service
public class ComplianceServiceSweepService {

    private static final String COMPLIANCE_RESOURCE = "ComplianceDocument";
    private static final String VEHICLE_RESOURCE = "Vehicle";

    private static final ActorContext SWEEP_ACTOR = new ActorContext(
            new SiteScopedPrincipal("sfl-fleet-compliance-scheduler", "Fleet compliance/service scheduler",
                    Set.of(SflRole.SERVICE_INTEGRATION, SflRole.SFL_ADMIN), Set.of("*"), true),
            "fleet-compliance-service-sweep");

    private final VehicleRepository vehicles;
    private final ComplianceDocumentRepository complianceDocuments;
    private final VehicleServiceRecordRepository serviceRecords;
    private final FleetWorkflowRaiser workflowRaiser;
    private final RuntimeConfigurationPort configuration;
    private final AuditPort auditPort;
    private final IntegrationEventPublisher eventPublisher;
    private final Clock clock;

    public ComplianceServiceSweepService(VehicleRepository vehicles,
            ComplianceDocumentRepository complianceDocuments, VehicleServiceRecordRepository serviceRecords,
            FleetWorkflowRaiser workflowRaiser, RuntimeConfigurationPort configuration, AuditPort auditPort,
            IntegrationEventPublisher eventPublisher, Clock clock) {
        this.vehicles = vehicles;
        this.complianceDocuments = complianceDocuments;
        this.serviceRecords = serviceRecords;
        this.workflowRaiser = workflowRaiser;
        this.configuration = configuration;
        this.auditPort = auditPort;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** Runs one complete pass and returns counts for scheduler logs and end-to-end assertions. */
    @Transactional
    public SweepResult evaluateOnce() {
        Instant now = clock.instant();
        int complianceUpdated = 0;
        int complianceWorkflows = 0;
        int serviceUpdated = 0;
        int serviceWorkflows = 0;

        List<Vehicle> scopedVehicles = vehicles.findAllInScope(gh.edu.clet.sfl.fleetlogistics.fleet.application
                .service.SiteScopeFilter.all());
        Map<java.util.UUID, Vehicle> vehiclesById = scopedVehicles.stream()
                .collect(java.util.stream.Collectors.toMap(Vehicle::id, vehicle -> vehicle));

        for (ComplianceDocument document : complianceDocuments.findInScope(gh.edu.clet.sfl.fleetlogistics.fleet
                .application.service.SiteScopeFilter.all())) {
            ComplianceDocument reassessed = document.reassessAt(now,
                    configuration.complianceExpiryWarningWindow(document.siteCode().value()),
                    document.metadata().modifiedBy(SWEEP_ACTOR.actorId(), now, SourceChannel.SCHEDULER,
                            SWEEP_ACTOR.correlationId()));
            if (reassessed != document) {
                complianceDocuments.save(reassessed);
                complianceUpdated++;
                auditPort.record(SWEEP_ACTOR, SourceChannel.SCHEDULER, reassessed.siteCode(), AuditAction.UPDATE,
                        COMPLIANCE_RESOURCE, reassessed.id().toString(), complianceImage(document),
                        complianceImage(reassessed));
                publishComplianceEvent(reassessed);
            }
            if ((reassessed.status() == ComplianceDocumentStatus.EXPIRING
                    || reassessed.status() == ComplianceDocumentStatus.EXPIRED)
                    && vehiclesById.containsKey(reassessed.vehicleId())) {
                workflowRaiser.raiseComplianceExpiry(reassessed, vehiclesById.get(reassessed.vehicleId()),
                        reassessed.status() == ComplianceDocumentStatus.EXPIRED, SWEEP_ACTOR,
                        SourceChannel.SCHEDULER);
                complianceWorkflows++;
            }
        }

        for (Vehicle vehicle : scopedVehicles) {
            VehicleServiceRecord latest = serviceRecords.findLatestByVehicle(vehicle.id()).orElse(null);
            if (latest == null) {
                continue;
            }
            VehicleServiceStatus reassessed = latest.deriveStatus(now, vehicle.odometer().value(),
                    configuration.serviceDueWarningWindow(vehicle.siteCode().value()));
            Vehicle updated = vehicle.withServiceStatus(reassessed, vehicle.metadata()
                    .modifiedBy(SWEEP_ACTOR.actorId(), now, SourceChannel.SCHEDULER, SWEEP_ACTOR.correlationId()));
            if (updated.serviceStatus() != vehicle.serviceStatus()) {
                vehicles.save(updated);
                serviceUpdated++;
                auditPort.record(SWEEP_ACTOR, SourceChannel.SCHEDULER, vehicle.siteCode(), AuditAction.UPDATE,
                        VEHICLE_RESOURCE, vehicle.id().toString(), vehicleImage(vehicle), vehicleImage(updated));
                publishServiceEvent(updated);
            }
            if (updated.serviceStatus() == VehicleServiceStatus.DUE
                    || updated.serviceStatus() == VehicleServiceStatus.OVERDUE) {
                workflowRaiser.raiseServiceDue(updated, updated.serviceStatus() == VehicleServiceStatus.OVERDUE,
                        SWEEP_ACTOR, SourceChannel.SCHEDULER);
                serviceWorkflows++;
            }
        }

        return new SweepResult(complianceUpdated, complianceWorkflows, serviceUpdated, serviceWorkflows);
    }

    private void publishComplianceEvent(ComplianceDocument document) {
        if (document.status() != ComplianceDocumentStatus.EXPIRING
                && document.status() != ComplianceDocumentStatus.EXPIRED) {
            return;
        }
        eventPublisher.publish(document.status() == ComplianceDocumentStatus.EXPIRED
                        ? FleetEventType.VEHICLE_COMPLIANCE_EXPIRED
                        : FleetEventType.VEHICLE_COMPLIANCE_EXPIRING,
                COMPLIANCE_RESOURCE, document.id().toString(), document.siteCode(), SWEEP_ACTOR,
                complianceImage(document));
    }

    private void publishServiceEvent(Vehicle vehicle) {
        if (vehicle.serviceStatus() != VehicleServiceStatus.DUE
                && vehicle.serviceStatus() != VehicleServiceStatus.OVERDUE) {
            return;
        }
        eventPublisher.publish(vehicle.serviceStatus() == VehicleServiceStatus.OVERDUE
                        ? FleetEventType.VEHICLE_SERVICE_OVERDUE
                        : FleetEventType.VEHICLE_SERVICE_DUE,
                VEHICLE_RESOURCE, vehicle.id().toString(), vehicle.siteCode(), SWEEP_ACTOR,
                vehicleImage(vehicle));
    }

    private static Map<String, Object> complianceImage(ComplianceDocument document) {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("documentId", document.id().toString());
        image.put("vehicleId", document.vehicleId().toString());
        image.put("siteCode", document.siteCode().value());
        image.put("documentType", document.documentType().name());
        image.put("expiresOn", document.expiresOn().toString());
        image.put("status", document.status().name());
        image.put("version", document.metadata().version());
        return image;
    }

    private static Map<String, Object> vehicleImage(Vehicle vehicle) {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("vehicleId", vehicle.id().toString());
        image.put("registrationNumber", vehicle.registrationNumber().value());
        image.put("siteCode", vehicle.siteCode().value());
        image.put("serviceStatus", vehicle.serviceStatus().name());
        image.put("availabilityStatus", vehicle.availabilityStatus().name());
        image.put("odometer", vehicle.odometer().value());
        image.put("version", vehicle.metadata().version());
        return image;
    }

    public record SweepResult(int complianceStatusesUpdated, int complianceWorkflowsRaised,
            int serviceStatusesUpdated, int serviceWorkflowsRaised) {

        public int totalWorkflowsRaised() {
            return complianceWorkflowsRaised + serviceWorkflowsRaised;
        }
    }
}
