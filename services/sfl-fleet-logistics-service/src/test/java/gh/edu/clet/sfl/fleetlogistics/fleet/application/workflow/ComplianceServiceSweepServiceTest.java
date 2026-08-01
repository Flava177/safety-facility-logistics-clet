package gh.edu.clet.sfl.fleetlogistics.fleet.application.workflow;

import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.ACCRA;
import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.event.FleetEventType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocument;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceRetentionClass;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Vehicle;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleServiceStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowPriority;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowSeverity;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy.SlaPolicy;
import gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures;
import gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetTestDoubles;
import gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetWorkflowTestDoubles;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Traces the scheduled compliance/service sweeps that harden S166 completion. */
class ComplianceServiceSweepServiceTest {

    private FleetTestDoubles.InMemoryVehicleRepository vehicles;
    private FleetTestDoubles.InMemoryComplianceDocumentRepository compliance;
    private FleetTestDoubles.InMemoryServiceRecordRepository serviceRecords;
    private FleetWorkflowTestDoubles.InMemoryFleetWorkflowRepository workflows;
    private FleetTestDoubles.RecordingAuditPort audit;
    private FleetTestDoubles.RecordingEventPublisher events;
    private ComplianceServiceSweepService sweep;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        vehicles = new FleetTestDoubles.InMemoryVehicleRepository();
        compliance = new FleetTestDoubles.InMemoryComplianceDocumentRepository();
        serviceRecords = new FleetTestDoubles.InMemoryServiceRecordRepository();
        workflows = new FleetWorkflowTestDoubles.InMemoryFleetWorkflowRepository();
        audit = new FleetTestDoubles.RecordingAuditPort(clock);
        events = new FleetTestDoubles.RecordingEventPublisher();

        var workflowService = new gh.edu.clet.sfl.fleetlogistics.fleet.application.service
                .FleetWorkflowApplicationService(workflows,
                new FleetWorkflowTestDoubles.MutableSlaRuleRepository().withRules(new SlaPolicy.SlaRule("default",
                        null, null, null, null, null, Duration.ofHours(1), Duration.ofHours(8),
                        SflRole.FLEET_MANAGER)),
                new gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetAccessPolicy(), audit,
                events, new FleetTestDoubles.RecordingNotificationPort(), clock);

        sweep = new ComplianceServiceSweepService(vehicles, compliance, serviceRecords, workflowService,
                new FleetTestDoubles.FixedRuntimeConfiguration().withComplianceWarning(Duration.ofDays(30))
                        .withServiceDueWarning(Duration.ofDays(14)),
                audit, events, clock);
    }

    @Test
    @DisplayName("sweep reassesses compliance, service status, events and workflow blockers")
    void sweep_reassesses_compliance_service_and_raises_work() {
        Vehicle vehicle = FleetFixtures.vehicle(UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "GT-1000-26", ACCRA);
        vehicles.save(vehicle);
        compliance.save(ComplianceDocument.register(UUID.randomUUID(), vehicle.id(), ACCRA,
                ComplianceDocumentType.INSURANCE_CERTIFICATE, "INS-2026-001", "NIC",
                FleetFixtures.TODAY.minusMonths(3), FleetFixtures.TODAY.plusDays(10), UUID.randomUUID(),
                EvidenceRetentionClass.COMPLIANCE_7_YEARS, NOW, Duration.ofDays(1), FleetFixtures.metadata()));
        serviceRecords.save(FleetFixtures.completedService(vehicle.id(), LocalDate.parse("2025-07-01"),
                40_000, LocalDate.parse("2026-07-01"), 43_000L));

        ComplianceServiceSweepService.SweepResult result = sweep.evaluateOnce();

        assertThat(result.complianceStatusesUpdated()).isEqualTo(1);
        assertThat(result.serviceStatusesUpdated()).isEqualTo(1);
        assertThat(result.totalWorkflowsRaised()).isEqualTo(2);
        assertThat(compliance.findByVehicle(vehicle.id())).singleElement()
                .satisfies(document -> assertThat(document.status()).isEqualTo(ComplianceDocumentStatus.EXPIRING));
        assertThat(vehicles.findById(vehicle.id())).get()
                .satisfies(updated -> assertThat(updated.serviceStatus()).isEqualTo(VehicleServiceStatus.OVERDUE));
        assertThat(workflows.findAllInScope(gh.edu.clet.sfl.fleetlogistics.fleet.application.service
                .SiteScopeFilter.all()))
                .extracting(item -> item.workflowType())
                .contains(FleetWorkflowType.COMPLIANCE_RENEWAL, FleetWorkflowType.SERVICE_SCHEDULING);
        assertThat(events.types()).contains(FleetEventType.VEHICLE_COMPLIANCE_EXPIRING,
                FleetEventType.VEHICLE_SERVICE_OVERDUE);
        assertThat(audit.hasRecord(AuditAction.UPDATE, "ComplianceDocument")).isTrue();
        assertThat(audit.hasRecord(AuditAction.UPDATE, "Vehicle")).isTrue();
    }
}
