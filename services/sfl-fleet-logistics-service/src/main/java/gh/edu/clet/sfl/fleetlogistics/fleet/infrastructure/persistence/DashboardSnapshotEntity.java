package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetDashboardApplicationService.DashboardIndicators;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetDashboardApplicationService.DashboardReconciliation;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetDashboardApplicationService.OperationsDashboardSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** JPA image of a generated operations dashboard snapshot. */
@Entity
@Table(name = "fleet_dashboard_snapshots", schema = "fleet_logistics")
class DashboardSnapshotEntity {

    @Id
    private UUID id;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "scope_key", nullable = false, length = 240)
    private String scopeKey;

    @Column(name = "site_code", length = 40)
    private String siteCode;

    @Column(nullable = false)
    private boolean stale;

    @Column(length = 2000)
    private String warnings;

    @Column(name = "vehicles_available", nullable = false)
    private long vehiclesAvailable;

    @Column(name = "expired_compliance", nullable = false)
    private long expiredCompliance;

    @Column(name = "service_due", nullable = false)
    private long serviceDue;

    @Column(name = "assignment_conflicts", nullable = false)
    private long assignmentConflicts;

    @Column(name = "readiness_blockers", nullable = false)
    private long readinessBlockers;

    @Column(name = "open_workflow_items", nullable = false)
    private long openWorkflowItems;

    @Column(name = "escalated_workflow_items", nullable = false)
    private long escalatedWorkflowItems;

    @Column(name = "integration_dead_letters", nullable = false)
    private long integrationDeadLetters;

    @Column(nullable = false)
    private long vehicles;

    @Column(name = "compliance_documents", nullable = false)
    private long complianceDocuments;

    @Column(nullable = false)
    private long trips;

    @Column(name = "workflow_items", nullable = false)
    private long workflowItems;

    @Column(name = "latest_service_records", nullable = false)
    private long latestServiceRecords;

    @Column(name = "recent_vehicle_locations", nullable = false)
    private long recentVehicleLocations;

    @Column(name = "snapshot_as_of", nullable = false)
    private Instant snapshotAsOf;

    @Column(name = "freshest_source_at", nullable = false)
    private Instant freshestSourceAt;

    protected DashboardSnapshotEntity() {
    }

    static DashboardSnapshotEntity from(OperationsDashboardSnapshot snapshot) {
        DashboardSnapshotEntity entity = new DashboardSnapshotEntity();
        entity.id = snapshot.id();
        entity.generatedAt = snapshot.generatedAt();
        entity.scopeKey = snapshot.scopeKey();
        entity.siteCode = snapshot.siteCode();
        entity.stale = snapshot.stale();
        entity.warnings = String.join("\n", snapshot.warnings());
        entity.vehiclesAvailable = snapshot.indicators().vehiclesAvailable();
        entity.expiredCompliance = snapshot.indicators().expiredCompliance();
        entity.serviceDue = snapshot.indicators().serviceDue();
        entity.assignmentConflicts = snapshot.indicators().assignmentConflicts();
        entity.readinessBlockers = snapshot.indicators().readinessBlockers();
        entity.openWorkflowItems = snapshot.indicators().openWorkflowItems();
        entity.escalatedWorkflowItems = snapshot.indicators().escalatedWorkflowItems();
        entity.integrationDeadLetters = snapshot.indicators().integrationDeadLetters();
        entity.vehicles = snapshot.reconciliation().vehicles();
        entity.complianceDocuments = snapshot.reconciliation().complianceDocuments();
        entity.trips = snapshot.reconciliation().trips();
        entity.workflowItems = snapshot.reconciliation().workflowItems();
        entity.latestServiceRecords = snapshot.reconciliation().latestServiceRecords();
        entity.recentVehicleLocations = snapshot.reconciliation().recentVehicleLocations();
        entity.snapshotAsOf = snapshot.snapshotAsOf();
        entity.freshestSourceAt = snapshot.freshestSourceAt();
        return entity;
    }

    OperationsDashboardSnapshot toDomain() {
        return new OperationsDashboardSnapshot(id, generatedAt, scopeKey, siteCode, stale,
                warnings == null || warnings.isBlank() ? List.of() : Arrays.asList(warnings.split("\n")),
                new DashboardIndicators(vehiclesAvailable, expiredCompliance, serviceDue, assignmentConflicts,
                        readinessBlockers, openWorkflowItems, escalatedWorkflowItems, integrationDeadLetters),
                new DashboardReconciliation(vehicles, complianceDocuments, trips, workflowItems,
                        latestServiceRecords, recentVehicleLocations),
                snapshotAsOf, freshestSourceAt);
    }
}
