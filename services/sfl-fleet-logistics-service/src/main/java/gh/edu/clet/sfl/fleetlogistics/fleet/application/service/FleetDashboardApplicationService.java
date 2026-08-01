package gh.edu.clet.sfl.fleetlogistics.fleet.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.ComplianceDocumentRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.DashboardSnapshotRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.FleetWorkflowRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IntegrationInboxRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.RuntimeConfigurationPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.TripRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleLocationRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleServiceRecordRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.DashboardDataStaleException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocument;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.IntegrationMessageStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Trip;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Vehicle;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleAvailabilityStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleServiceStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowPriority;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operations dashboards and reports for SRS-SFL-S166-05. */
@Service
public class FleetDashboardApplicationService {

    private final VehicleRepository vehicles;
    private final ComplianceDocumentRepository complianceDocuments;
    private final VehicleServiceRecordRepository serviceRecords;
    private final TripRepository trips;
    private final FleetWorkflowRepository workflowItems;
    private final VehicleLocationRepository locations;
    private final IntegrationInboxRepository integrationInbox;
    private final DashboardSnapshotRepository snapshots;
    private final RuntimeConfigurationPort configuration;
    private final FleetAccessPolicy accessPolicy;
    private final AuditPort auditPort;
    private final Clock clock;

    public FleetDashboardApplicationService(VehicleRepository vehicles,
            ComplianceDocumentRepository complianceDocuments, VehicleServiceRecordRepository serviceRecords,
            TripRepository trips, FleetWorkflowRepository workflowItems, VehicleLocationRepository locations,
            IntegrationInboxRepository integrationInbox, DashboardSnapshotRepository snapshots,
            RuntimeConfigurationPort configuration, FleetAccessPolicy accessPolicy, AuditPort auditPort,
            Clock clock) {
        this.vehicles = vehicles;
        this.complianceDocuments = complianceDocuments;
        this.serviceRecords = serviceRecords;
        this.trips = trips;
        this.workflowItems = workflowItems;
        this.locations = locations;
        this.integrationInbox = integrationInbox;
        this.snapshots = snapshots;
        this.configuration = configuration;
        this.accessPolicy = accessPolicy;
        this.auditPort = auditPort;
        this.clock = clock;
    }

    @Transactional
    public OperationsDashboardSnapshot operations(DashboardFilter filter, ActorContext actor, boolean requireFresh) {
        accessPolicy.requirePermission(actor, SflPermission.FLEET_DASHBOARD_READ, "OperationsDashboard");
        SiteScopeFilter scope = scoped(filter, actor);
        Instant now = clock.instant();

        List<Vehicle> scopedVehicles = vehicles.findAllInScope(scope).stream()
                .filter(vehicle -> filter.siteCode() == null || vehicle.siteCode().value().equals(filter.siteCode()))
                .toList();
        List<ComplianceDocument> scopedCompliance = complianceDocuments.findInScope(scope).stream()
                .filter(document -> filter.siteCode() == null || document.siteCode().value().equals(filter.siteCode()))
                .toList();
        List<Trip> scopedTrips = trips.findAllInScope(scope).stream()
                .filter(trip -> filter.siteCode() == null || trip.siteCode().value().equals(filter.siteCode()))
                .filter(trip -> filter.operatingMode() == null || trip.operatingMode() == filter.operatingMode())
                .filter(trip -> filter.from() == null || !trip.plannedPeriod().end().isBefore(filter.from()))
                .filter(trip -> filter.to() == null || !trip.plannedPeriod().start().isAfter(filter.to()))
                .toList();
        List<FleetWorkflowItem> scopedWorkflow = workflowItems.findAllInScope(scope).stream()
                .filter(item -> filter.siteCode() == null || item.siteCode().value().equals(filter.siteCode()))
                .filter(item -> filter.workflowStatus() == null || item.status() == filter.workflowStatus())
                .filter(item -> filter.priority() == null || item.priority() == filter.priority())
                .filter(item -> filter.owner() == null || filter.owner().equalsIgnoreCase(item.assignee()))
                .filter(item -> filter.operatingMode() == null || item.operatingMode() == filter.operatingMode())
                .toList();

        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        long expiredCompliance = scopedCompliance.stream()
                .filter(document -> document.status() == ComplianceDocumentStatus.EXPIRED
                        || document.expiresOn().isBefore(today))
                .count();
        long serviceDue = scopedVehicles.stream()
                .filter(vehicle -> vehicle.serviceStatus() == VehicleServiceStatus.DUE
                        || vehicle.serviceStatus() == VehicleServiceStatus.OVERDUE)
                .count();
        long assignmentConflicts = assignmentConflicts(scopedTrips);
        long readinessBlockers = scopedVehicles.stream()
                .filter(vehicle -> vehicle.serviceStatus().blocksAssignment()
                        || vehicle.availabilityStatus() == VehicleAvailabilityStatus.UNAVAILABLE
                        || hasExpiredCompliance(vehicle, scopedCompliance, today))
                .count();

        Instant freshestSource = freshestSource(scopedVehicles, scopedCompliance, scopedTrips, scopedWorkflow);
        boolean stale = freshestSource.plus(configuration.dashboardFreshnessThreshold(filter.siteCode()))
                .isBefore(now);
        List<String> warnings = new ArrayList<>();
        // Staleness is deliberately NOT a warning sentence.
        //
        // It is already on the snapshot as `stale`, and the dashboard renders that beside the
        // generated-at timestamp as a chip. Adding a sentence saying the same thing put a permanent
        // amber banner across the top of the dashboard on every environment where data does not
        // change often — which is every environment before go-live — and a banner that is always
        // there is a banner nobody reads, including on the day it matters.
        //
        // `FleetErrorCode.FLEET_DASHBOARD_DATA_STALE` is untouched: that is the error path, for a
        // caller that asked for data the service will not vouch for. This list is advisory, and what
        // belongs in it is something an operator can act on.
        if (integrationInbox.countByStatus(IntegrationMessageStatus.DEAD_LETTER) > 0) {
            warnings.add("Integration dead-letter messages require replay or operator review.");
        }

        OperationsDashboardSnapshot snapshot = new OperationsDashboardSnapshot(UUID.randomUUID(), now,
                scopeKey(scope, filter), filter.siteCode(), stale, warnings,
                new DashboardIndicators(scopedVehicles.stream().filter(vehicle -> vehicle.availabilityStatus().isFree()).count(),
                        expiredCompliance, serviceDue, assignmentConflicts, readinessBlockers,
                        scopedWorkflow.stream().filter(item -> item.status().isLive()).count(),
                        scopedWorkflow.stream().filter(item -> item.status() == FleetWorkflowStatus.ESCALATED).count(),
                        integrationInbox.countByStatus(IntegrationMessageStatus.DEAD_LETTER)),
                new DashboardReconciliation(scopedVehicles.size(), scopedCompliance.size(), scopedTrips.size(),
                        scopedWorkflow.size(), serviceRecords.findLatestInScope(scope).size(),
                        locations.findRecentInScope(scope, 500).size()),
                now, freshestSource);
        OperationsDashboardSnapshot saved = snapshots.save(snapshot);
        auditPort.record(actor, SourceChannel.API, SiteCode.of(saved.siteCode() == null ? "UNSCOPED" : saved.siteCode()),
                AuditAction.DASHBOARD_ACCESSED, "OperationsDashboard", saved.id().toString(), null,
                saved.auditImage());
        if (requireFresh && saved.stale()) {
            throw new DashboardDataStaleException(saved.auditImage());
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<DashboardDrilldownRow> drilldown(String indicator, DashboardFilter filter, ActorContext actor) {
        SiteScopeFilter scope = scoped(filter, actor);
        return switch (indicator.toUpperCase(Locale.ROOT)) {
            case "EXPIRED_COMPLIANCE" -> complianceDocuments.findInScope(scope).stream()
                    .filter(document -> document.status() == ComplianceDocumentStatus.EXPIRED
                            || document.expiresOn().isBefore(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC)))
                    .map(document -> row(actor, document.siteCode(), "ComplianceDocument", document.id().toString(),
                            document.documentType().name() + " expired " + document.expiresOn()))
                    .toList();
            case "SERVICE_DUE" -> vehicles.findAllInScope(scope).stream()
                    .filter(vehicle -> vehicle.serviceStatus() == VehicleServiceStatus.DUE
                            || vehicle.serviceStatus() == VehicleServiceStatus.OVERDUE)
                    .map(vehicle -> row(actor, vehicle.siteCode(), "Vehicle", vehicle.id().toString(),
                            vehicle.registrationNumber().value() + " service " + vehicle.serviceStatus()))
                    .toList();
            case "READINESS_BLOCKERS" -> vehicles.findAllInScope(scope).stream()
                    .filter(vehicle -> vehicle.serviceStatus().blocksAssignment()
                            || vehicle.availabilityStatus() == VehicleAvailabilityStatus.UNAVAILABLE)
                    .map(vehicle -> row(actor, vehicle.siteCode(), "Vehicle", vehicle.id().toString(),
                            vehicle.registrationNumber().value() + " readiness blocked"))
                    .toList();
            case "ASSIGNMENT_CONFLICTS" -> trips.findAllInScope(scope).stream()
                    .filter(Trip::holdsAssignment)
                    .collect(Collectors.groupingBy(Trip::vehicleId))
                    .values().stream()
                    .filter(group -> group.size() > 1)
                    .flatMap(List::stream)
                    .map(trip -> row(actor, trip.siteCode(), "Trip", trip.id().toString(),
                            trip.tripNumber() + " conflicts on vehicle"))
                    .toList();
            default -> List.of();
        };
    }

    @Transactional
    public GoLiveReadinessReport goLiveReadiness(DashboardFilter filter, ActorContext actor) {
        accessPolicy.requirePermission(actor, SflPermission.FLEET_REPORT_EXPORT, "GoLiveReadinessReport");
        OperationsDashboardSnapshot snapshot = operations(filter, actor, false);
        boolean ready = !snapshot.stale()
                && snapshot.indicators().expiredCompliance() == 0
                && snapshot.indicators().assignmentConflicts() == 0
                && snapshot.indicators().integrationDeadLetters() == 0;
        GoLiveReadinessReport report = new GoLiveReadinessReport(UUID.randomUUID(), clock.instant(), ready,
                snapshot, ready ? List.of() : List.of("Resolve stale data, compliance, assignment conflict or integration blockers."));
        auditPort.record(actor, SourceChannel.API, SiteCode.of(snapshot.siteCode() == null ? "UNSCOPED" : snapshot.siteCode()),
                AuditAction.REPORT_EXPORTED, "GoLiveReadinessReport", report.id().toString(), null,
                Map.of("ready", ready, "snapshotId", snapshot.id().toString()));
        return report;
    }

    private DashboardDrilldownRow row(ActorContext actor, SiteCode site, String resourceType, String resourceId,
            String summary) {
        accessPolicy.requireDrilldown(actor, SflPermission.FLEET_DASHBOARD_DRILLDOWN, site, resourceType, resourceId);
        return new DashboardDrilldownRow(resourceType, resourceId, site.value(), summary);
    }

    private SiteScopeFilter scoped(DashboardFilter filter, ActorContext actor) {
        SiteScopeFilter actorScope = accessPolicy.requireSiteScopeFilter(actor);
        if (filter.siteCode() == null || actorScope.allSites()) {
            return actorScope;
        }
        return SiteScopeFilter.of(java.util.Set.of(filter.siteCode()));
    }

    private static long assignmentConflicts(List<Trip> trips) {
        return countDuplicates(trips.stream().filter(Trip::holdsAssignment).map(Trip::vehicleId).toList())
                + countDuplicates(trips.stream().filter(Trip::holdsAssignment).map(Trip::driverId).toList());
    }

    private static long countDuplicates(List<UUID> ids) {
        return ids.stream().filter(java.util.Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .values().stream().filter(count -> count > 1).mapToLong(Long::longValue).sum();
    }

    private static boolean hasExpiredCompliance(Vehicle vehicle, List<ComplianceDocument> documents, LocalDate today) {
        return documents.stream()
                .filter(document -> document.vehicleId().equals(vehicle.id()))
                .anyMatch(document -> document.status() == ComplianceDocumentStatus.EXPIRED
                        || document.expiresOn().isBefore(today));
    }

    private static Instant freshestSource(List<Vehicle> vehicles, List<ComplianceDocument> compliance,
            List<Trip> trips, List<FleetWorkflowItem> workflow) {
        return java.util.stream.Stream.of(
                        vehicles.stream().map(vehicle -> vehicle.metadata().lastModifiedAt()),
                        compliance.stream().map(document -> document.metadata().lastModifiedAt()),
                        trips.stream().map(trip -> trip.metadata().lastModifiedAt()),
                        workflow.stream().map(item -> item.metadata().lastModifiedAt()))
                .flatMap(Function.identity())
                .max(Comparator.naturalOrder())
                .orElse(Instant.EPOCH);
    }

    private static String scopeKey(SiteScopeFilter scope, DashboardFilter filter) {
        if (filter.siteCode() != null) {
            return filter.siteCode();
        }
        return scope.allSites() ? "*" : scope.sites().stream().sorted().collect(Collectors.joining(","));
    }

    public record DashboardFilter(String siteCode, FleetWorkflowStatus workflowStatus, WorkflowPriority priority,
            String owner, OperatingMode operatingMode, Instant from, Instant to) {
        public DashboardFilter {
            siteCode = siteCode == null || siteCode.isBlank() ? null : siteCode.strip().toUpperCase(Locale.ROOT);
            owner = owner == null || owner.isBlank() ? null : owner.strip();
        }
    }

    public record OperationsDashboardSnapshot(UUID id, Instant generatedAt, String scopeKey, String siteCode,
            boolean stale, List<String> warnings, DashboardIndicators indicators,
            DashboardReconciliation reconciliation, Instant snapshotAsOf, Instant freshestSourceAt) {

        public Map<String, Object> auditImage() {
            Map<String, Object> image = new LinkedHashMap<>();
            image.put("snapshotId", id.toString());
            image.put("scopeKey", scopeKey);
            image.put("siteCode", siteCode);
            image.put("stale", stale);
            image.put("warnings", warnings);
            image.put("snapshotAsOf", snapshotAsOf.toString());
            image.put("freshestSourceAt", freshestSourceAt.toString());
            return image;
        }
    }

    public record DashboardIndicators(long vehiclesAvailable, long expiredCompliance, long serviceDue,
            long assignmentConflicts, long readinessBlockers, long openWorkflowItems, long escalatedWorkflowItems,
            long integrationDeadLetters) {
    }

    public record DashboardReconciliation(long vehicles, long complianceDocuments, long trips, long workflowItems,
            long latestServiceRecords, long recentVehicleLocations) {
    }

    public record DashboardDrilldownRow(String resourceType, String resourceId, String siteCode, String summary) {
    }

    public record GoLiveReadinessReport(UUID id, Instant generatedAt, boolean ready,
            OperationsDashboardSnapshot snapshot, List<String> blockers) {
    }
}
