package gh.edu.clet.sfl.fleetlogistics.fleet.application.service;

import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.ACCRA;
import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.DashboardSnapshotRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IntegrationInboxRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleLocationRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetDashboardApplicationService.DashboardFilter;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetDashboardApplicationService.OperationsDashboardSnapshot;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.DashboardDataStaleException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RestrictedDrilldownException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.IntegrationInboxMessage;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.IntegrationMessageStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Vehicle;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleLocationSnapshot;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleServiceStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures;
import gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetTestDoubles;
import gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetWorkflowTestDoubles;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Traces: SRS-SFL-S166-05 operations dashboards, drilldowns and reports. */
class FleetDashboardApplicationServiceTest {

    private FleetTestDoubles.InMemoryVehicleRepository vehicles;
    private FleetTestDoubles.InMemoryComplianceDocumentRepository compliance;
    private FleetTestDoubles.InMemoryServiceRecordRepository serviceRecords;
    private FleetWorkflowTestDoubles.InMemoryTripRepository trips;
    private FleetWorkflowTestDoubles.InMemoryFleetWorkflowRepository workflows;
    private InMemoryLocationRepository locations;
    private InMemoryIntegrationInbox inbox;
    private InMemoryDashboardSnapshots snapshots;
    private FleetTestDoubles.RecordingAuditPort audit;
    private FleetDashboardApplicationService service;

    @BeforeEach
    void setUp() {
        vehicles = new FleetTestDoubles.InMemoryVehicleRepository();
        compliance = new FleetTestDoubles.InMemoryComplianceDocumentRepository();
        serviceRecords = new FleetTestDoubles.InMemoryServiceRecordRepository();
        trips = new FleetWorkflowTestDoubles.InMemoryTripRepository();
        workflows = new FleetWorkflowTestDoubles.InMemoryFleetWorkflowRepository();
        locations = new InMemoryLocationRepository();
        inbox = new InMemoryIntegrationInbox();
        snapshots = new InMemoryDashboardSnapshots();
        audit = new FleetTestDoubles.RecordingAuditPort(Clock.fixed(NOW, ZoneOffset.UTC));

        Vehicle available = FleetFixtures.vehicle();
        Vehicle due = FleetFixtures.vehicle(UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "GT-2222-26", ACCRA).withServiceStatus(VehicleServiceStatus.DUE, FleetFixtures.metadata());
        vehicles.save(available);
        vehicles.save(due);
        compliance.save(FleetFixtures.expiredInsurance(due.id()));

        service = dashboardService(Clock.fixed(NOW, ZoneOffset.UTC),
                new FleetTestDoubles.FixedRuntimeConfiguration().withDashboardFreshness(Duration.ofMinutes(15)));
    }

    @Test
    @DisplayName("operations snapshot reports SRS indicators, reconciliation counts and audit access")
    void operations_snapshot_reports_indicators_and_reconciliation() {
        OperationsDashboardSnapshot snapshot = service.operations(filter("ACCRA"),
                FleetTestDoubles.fleetManager("ACCRA"), false);

        assertThat(snapshot.indicators().vehiclesAvailable()).isEqualTo(2);
        assertThat(snapshot.indicators().expiredCompliance()).isEqualTo(1);
        assertThat(snapshot.indicators().serviceDue()).isEqualTo(1);
        assertThat(snapshot.indicators().readinessBlockers()).isEqualTo(1);
        assertThat(snapshot.reconciliation().vehicles()).isEqualTo(2);
        assertThat(snapshots.latestForScope("ACCRA")).isPresent();
        assertThat(audit.hasRecord(AuditAction.DASHBOARD_ACCESSED, "OperationsDashboard")).isTrue();
    }

    @Test
    @DisplayName("requireFresh raises the SRS Data Stale error when source data is outside threshold")
    void require_fresh_raises_stale_error() {
        service = dashboardService(Clock.fixed(NOW.plus(Duration.ofHours(2)), ZoneOffset.UTC),
                new FleetTestDoubles.FixedRuntimeConfiguration().withDashboardFreshness(Duration.ofMinutes(15)));

        assertThatThrownBy(() -> service.operations(filter("ACCRA"), FleetTestDoubles.fleetManager("ACCRA"),
                true))
                .isInstanceOf(DashboardDataStaleException.class);
    }

    @Test
    @DisplayName("drilldown is restricted to roles with source-record permission")
    void drilldown_requires_drilldown_permission() {
        assertThat(service.drilldown("SERVICE_DUE", filter("ACCRA"), FleetTestDoubles.fleetManager("ACCRA")))
                .singleElement()
                .satisfies(row -> assertThat(row.resourceType()).isEqualTo("Vehicle"));

        assertThatThrownBy(() -> service.drilldown("SERVICE_DUE", filter("ACCRA"),
                FleetTestDoubles.driver("driver@clet.edu.gh", "ACCRA")))
                .isInstanceOf(RestrictedDrilldownException.class);
    }

    @Test
    @DisplayName("go-live readiness report blocks release when SRS dashboard blockers remain")
    void go_live_readiness_blocks_on_dashboard_gaps() {
        var report = service.goLiveReadiness(filter("ACCRA"), FleetTestDoubles.complianceOfficer("ACCRA"));

        assertThat(report.ready()).isFalse();
        assertThat(report.snapshot().indicators().expiredCompliance()).isEqualTo(1);
        assertThat(audit.hasRecord(AuditAction.REPORT_EXPORTED, "GoLiveReadinessReport")).isTrue();
    }

    private FleetDashboardApplicationService dashboardService(Clock clock,
            FleetTestDoubles.FixedRuntimeConfiguration configuration) {
        return new FleetDashboardApplicationService(vehicles, compliance, serviceRecords, trips, workflows,
                locations, inbox, snapshots, configuration, new FleetAccessPolicy(), audit, clock);
    }

    private static DashboardFilter filter(String siteCode) {
        return new DashboardFilter(siteCode, null, null, null, null, null, null);
    }

    private static final class InMemoryDashboardSnapshots implements DashboardSnapshotRepository {

        private final Map<String, OperationsDashboardSnapshot> store = new LinkedHashMap<>();

        @Override
        public OperationsDashboardSnapshot save(OperationsDashboardSnapshot snapshot) {
            store.put(snapshot.scopeKey(), snapshot);
            return snapshot;
        }

        @Override
        public Optional<OperationsDashboardSnapshot> latestForScope(String scopeKey) {
            return Optional.ofNullable(store.get(scopeKey));
        }
    }

    private static final class InMemoryIntegrationInbox implements IntegrationInboxRepository {

        @Override
        public IntegrationInboxMessage save(IntegrationInboxMessage message) {
            return message;
        }

        @Override
        public Optional<IntegrationInboxMessage> findById(UUID id) {
            return Optional.empty();
        }

        @Override
        public Optional<IntegrationInboxMessage> findBySourceAndIdempotencyKey(String sourceSystem,
                String idempotencyKey) {
            return Optional.empty();
        }

        @Override
        public List<IntegrationInboxMessage> findRecent(int limit) {
            return List.of();
        }

        @Override
        public long countByStatus(IntegrationMessageStatus status) {
            return 0;
        }
    }

    private static final class InMemoryLocationRepository implements VehicleLocationRepository {

        @Override
        public VehicleLocationSnapshot save(VehicleLocationSnapshot snapshot) {
            return snapshot;
        }

        @Override
        public Optional<VehicleLocationSnapshot> findLatestByVehicle(UUID vehicleId) {
            return Optional.empty();
        }

        @Override
        public List<VehicleLocationSnapshot> findRecentInScope(SiteScopeFilter scope, int limit) {
            return List.of();
        }
    }
}
