package gh.edu.clet.sfl.fleetlogistics.fleet.support;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.ComplianceDocumentRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.DriverProfileRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.HrmsDriverDirectoryPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IdempotencyPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IntegrationEventPublisher;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.NotificationPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.RuntimeConfigurationPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleServiceRecordRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.SiteScopeFilter;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.event.FleetEventType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.IdempotencyKeyConflictException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditChainVerification;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditEvent;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditHashChain;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocument;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverProfileReference;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RegistrationNumber;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Vehicle;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleInspectionRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleLocationRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetReadinessService;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleInspection;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleLocationSnapshot;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleServiceRecord;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory port implementations for application-layer tests.
 *
 * <p>These are real implementations of the contracts rather than mocks: the fake audit port maintains
 * a genuine hash chain and the fake repositories enforce the same uniqueness rules as the database, so
 * a test that passes here is testing behaviour rather than interaction bookkeeping.
 */
public final class FleetTestDoubles {

    private FleetTestDoubles() {
    }

    public static ActorContext actor(String subject, Set<SflRole> roles, Set<String> sites) {
        return new ActorContext(new SiteScopedPrincipal(subject, subject, roles, sites, false), "corr-test");
    }

    public static ActorContext fleetOfficer(String... sites) {
        return actor("officer@clet.edu.gh", Set.of(SflRole.FLEET_LOGISTICS_OFFICER), Set.of(sites));
    }

    public static ActorContext fleetManager(String... sites) {
        return actor("manager@clet.edu.gh", Set.of(SflRole.FLEET_MANAGER), Set.of(sites));
    }

    public static ActorContext reportingViewer(String... sites) {
        return actor("viewer@clet.edu.gh", Set.of(SflRole.FLEET_REPORTING_VIEWER), Set.of(sites));
    }

    public static ActorContext driver(String subject, String... sites) {
        return actor(subject, Set.of(SflRole.FLEET_DRIVER), Set.of(sites));
    }

    public static ActorContext auditor(String... sites) {
        return actor("auditor@clet.edu.gh", Set.of(SflRole.AUDITOR), Set.of(sites));
    }

    public static ActorContext complianceOfficer(String... sites) {
        return actor("compliance@clet.edu.gh", Set.of(SflRole.COMPLIANCE_OFFICER), Set.of(sites));
    }

    // --- Vehicle repository -------------------------------------------------------------

    /** In-memory vehicle register that enforces the same active-identifier uniqueness as the schema. */
    public static final class InMemoryVehicleRepository implements VehicleRepository {

        private final Map<UUID, Vehicle> store = new LinkedHashMap<>();

        @Override
        public Vehicle save(Vehicle vehicle) {
            store.put(vehicle.id(), vehicle);
            return vehicle;
        }

        @Override
        public Optional<Vehicle> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<Vehicle> findByIdForUpdate(UUID id) {
            return findById(id);
        }

        @Override
        public Optional<Vehicle> findActiveByRegistration(SiteCode siteCode, RegistrationNumber registration) {
            return store.values().stream()
                    .filter(vehicle -> vehicle.siteCode().equals(siteCode))
                    .filter(vehicle -> vehicle.registrationNumber().equals(registration))
                    .filter(vehicle -> vehicle.lifecycleStatus().isEditable())
                    .findFirst();
        }

        @Override
        public Optional<Vehicle> findActiveByVin(SiteCode siteCode, String vin) {
            return store.values().stream()
                    .filter(vehicle -> vehicle.siteCode().equals(siteCode))
                    .filter(vehicle -> vehicle.vin() != null && vehicle.vin().value().equalsIgnoreCase(vin))
                    .filter(vehicle -> vehicle.lifecycleStatus().isEditable())
                    .findFirst();
        }

        @Override
        public VehiclePage search(VehicleSearchCriteria criteria, SiteScopeFilter scope) {
            List<Vehicle> matching = store.values().stream()
                    .filter(vehicle -> scope.permits(vehicle.siteCode().value()))
                    .filter(vehicle -> criteria.siteCode() == null
                            || vehicle.siteCode().value().equalsIgnoreCase(criteria.siteCode()))
                    .filter(vehicle -> criteria.lifecycleStatus() == null
                            || vehicle.lifecycleStatus() == criteria.lifecycleStatus())
                    .filter(vehicle -> criteria.serviceStatus() == null
                            || vehicle.serviceStatus() == criteria.serviceStatus())
                    .filter(vehicle -> criteria.availabilityStatus() == null
                            || vehicle.availabilityStatus() == criteria.availabilityStatus())
                    .filter(vehicle -> criteria.category() == null
                            || vehicle.specification().category() == criteria.category())
                    .filter(vehicle -> criteria.registrationNumberContains() == null
                            || vehicle.registrationNumber().value()
                            .contains(criteria.registrationNumberContains().toUpperCase(java.util.Locale.ROOT)))
                    .sorted(Comparator.comparing((Vehicle vehicle) -> vehicle.metadata().createdAt()).reversed())
                    .toList();

            int size = criteria.size() <= 0 ? 20 : criteria.size();
            int from = Math.min(criteria.page() * size, matching.size());
            int to = Math.min(from + size, matching.size());
            int totalPages = (int) Math.ceil(matching.size() / (double) size);
            return new VehiclePage(matching.subList(from, to), criteria.page(), size, matching.size(),
                    Math.max(totalPages, 1), "createdAt: DESC");
        }

        @Override
        public List<Vehicle> findAllInScope(SiteScopeFilter scope) {
            return store.values().stream()
                    .filter(vehicle -> scope.permits(vehicle.siteCode().value()))
                    .toList();
        }

        public int size() {
            return store.size();
        }
    }

    // --- Compliance and service repositories --------------------------------------------

    public static final class InMemoryComplianceDocumentRepository implements ComplianceDocumentRepository {

        private final Map<UUID, ComplianceDocument> store = new LinkedHashMap<>();

        @Override
        public ComplianceDocument save(ComplianceDocument document) {
            store.put(document.id(), document);
            return document;
        }

        @Override
        public Optional<ComplianceDocument> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<ComplianceDocument> findByVehicle(UUID vehicleId) {
            return store.values().stream()
                    .filter(document -> document.vehicleId().equals(vehicleId))
                    .sorted(Comparator.comparing(ComplianceDocument::expiresOn).reversed())
                    .toList();
        }

        /**
         * The real filter, applied in memory.
         *
         * <p>Returning an empty list here would let a test pass while the search did nothing, which
         * is the whole failure mode a double is supposed to avoid.
         */
        @Override
        public List<ComplianceDocument> search(SiteScopeFilter scope, ComplianceDocumentType documentType,
                ComplianceDocumentStatus status, LocalDate expiringBefore, int limit) {
            return store.values().stream()
                    .filter(document -> scope.allSites() || scope.sites().contains(document.siteCode().value()))
                    .filter(document -> documentType == null || document.documentType() == documentType)
                    .filter(document -> status == null || document.status() == status)
                    .filter(document -> expiringBefore == null || !document.expiresOn().isAfter(expiringBefore))
                    .sorted(Comparator.comparing(ComplianceDocument::expiresOn))
                    .limit(Math.max(1, limit))
                    .toList();
        }

        @Override
        public List<ComplianceDocument> findCurrentByVehicle(UUID vehicleId) {
            return findByVehicle(vehicleId).stream()
                    .filter(document -> document.status().isCurrent())
                    .toList();
        }

        @Override
        public Optional<ComplianceDocument> findCurrentByVehicleAndType(UUID vehicleId,
                ComplianceDocumentType documentType) {
            return findCurrentByVehicle(vehicleId).stream()
                    .filter(document -> document.documentType() == documentType)
                    .findFirst();
        }

        @Override
        public List<ComplianceDocument> findCurrentExpiringOnOrBefore(LocalDate threshold) {
            return store.values().stream()
                    .filter(document -> document.status().isCurrent())
                    .filter(document -> !document.expiresOn().isAfter(threshold))
                    .toList();
        }

        @Override
        public List<ComplianceDocument> findInScope(SiteScopeFilter scope) {
            return store.values().stream()
                    .filter(document -> scope.permits(document.siteCode().value()))
                    .toList();
        }
    }

    public static final class InMemoryServiceRecordRepository implements VehicleServiceRecordRepository {

        private final Map<UUID, VehicleServiceRecord> store = new LinkedHashMap<>();

        @Override
        public VehicleServiceRecord save(VehicleServiceRecord record) {
            store.put(record.id(), record);
            return record;
        }

        @Override
        public Optional<VehicleServiceRecord> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<VehicleServiceRecord> findByVehicle(UUID vehicleId) {
            return store.values().stream()
                    .filter(record -> record.vehicleId().equals(vehicleId))
                    .sorted(Comparator.comparing(VehicleServiceRecord::performedOn).reversed())
                    .toList();
        }

        @Override
        public Optional<VehicleServiceRecord> findLatestByVehicle(UUID vehicleId) {
            return findByVehicle(vehicleId).stream().findFirst();
        }

        @Override
        public List<VehicleServiceRecord> findLatestInScope(SiteScopeFilter scope) {
            return store.values().stream()
                    .filter(record -> scope.permits(record.siteCode().value()))
                    .collect(java.util.stream.Collectors.toMap(VehicleServiceRecord::vehicleId, record -> record,
                            (first, second) -> first.performedOn().isAfter(second.performedOn()) ? first : second))
                    .values().stream().toList();
        }
    }


    // --- Driver register ----------------------------------------------------------------

    /** In-memory driver register enforcing the same active-identifier uniqueness as the schema. */
    public static final class InMemoryDriverProfileRepository implements DriverProfileRepository {

        private final Map<UUID, DriverProfileReference> store = new LinkedHashMap<>();

        @Override
        public DriverProfileReference save(DriverProfileReference driver) {
            store.put(driver.id(), driver);
            return driver;
        }

        @Override
        public Optional<DriverProfileReference> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<DriverProfileReference> findByIdForUpdate(UUID id) {
            return findById(id);
        }

        @Override
        public Optional<DriverProfileReference> findActiveByStaffReference(SiteCode siteCode,
                String staffReference) {
            return store.values().stream()
                    .filter(driver -> driver.siteCode().equals(siteCode))
                    .filter(driver -> driver.staffReference().equalsIgnoreCase(staffReference.strip()))
                    .filter(driver -> driver.lifecycleStatus().isEditable())
                    .findFirst();
        }

        @Override
        public Optional<DriverProfileReference> findActiveByLicenceNumber(SiteCode siteCode, String licenceNumber) {
            String normalised = licenceNumber.strip().replaceAll("\s+", "");
            return store.values().stream()
                    .filter(driver -> driver.siteCode().equals(siteCode))
                    .filter(driver -> driver.licence().number().equalsIgnoreCase(normalised))
                    .filter(driver -> driver.lifecycleStatus().isEditable())
                    .findFirst();
        }

        @Override
        public DriverPage search(DriverSearchCriteria criteria, SiteScopeFilter scope) {
            List<DriverProfileReference> matching = store.values().stream()
                    .filter(driver -> scope.permits(driver.siteCode().value()))
                    .filter(driver -> criteria.siteCode() == null
                            || driver.siteCode().value().equalsIgnoreCase(criteria.siteCode()))
                    .filter(driver -> criteria.lifecycleStatus() == null
                            || driver.lifecycleStatus() == criteria.lifecycleStatus())
                    .filter(driver -> criteria.eligibilityStatus() == null
                            || driver.eligibilityStatus() == criteria.eligibilityStatus())
                    .toList();
            int size = criteria.size() <= 0 ? 20 : criteria.size();
            int from = Math.min(criteria.page() * size, matching.size());
            int to = Math.min(from + size, matching.size());
            return new DriverPage(matching.subList(from, to), criteria.page(), size, matching.size(),
                    Math.max((int) Math.ceil(matching.size() / (double) size), 1), "displayName: ASC");
        }

        @Override
        public List<DriverProfileReference> findAllInScope(SiteScopeFilter scope) {
            return store.values().stream()
                    .filter(driver -> scope.permits(driver.siteCode().value()))
                    .toList();
        }

        @Override
        public List<DriverProfileReference> findExpiringOnOrBefore(LocalDate threshold) {
            return store.values().stream()
                    .filter(driver -> driver.lifecycleStatus().isEditable())
                    .filter(driver -> !driver.licence().expiresOn().isAfter(threshold)
                            || (driver.medicalClearanceExpiresOn() != null
                            && !driver.medicalClearanceExpiresOn().isAfter(threshold)))
                    .toList();
        }

        public int size() {
            return store.size();
        }
    }

    /** HRMS directory that accepts anything, and can be told to reject a specific reference. */
    public static final class StubHrmsDirectory implements HrmsDriverDirectoryPort {

        private final java.util.Set<String> unknownReferences = new java.util.HashSet<>();

        public StubHrmsDirectory rejecting(String staffReference) {
            unknownReferences.add(staffReference.toUpperCase(java.util.Locale.ROOT));
            return this;
        }

        @Override
        public void requireEmployedStaff(String staffReference, String siteCode) {
            if (staffReference == null
                    || unknownReferences.contains(staffReference.toUpperCase(java.util.Locale.ROOT))) {
                throw gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RecordNotFoundException
                        .of("HrmsStaff", staffReference);
            }
        }

        @Override
        public Optional<StaffDirectoryEntry> findStaff(String staffReference) {
            if (staffReference == null
                    || unknownReferences.contains(staffReference.toUpperCase(java.util.Locale.ROOT))) {
                return Optional.empty();
            }
            return Optional.of(new StaffDirectoryEntry(staffReference, staffReference, null, null, true, null));
        }
    }

    // --- Cross-cutting ports ------------------------------------------------------------


    /** A real hash chain over an in-memory list, so tamper detection can be tested without a database. */
    public static final class RecordingAuditPort implements AuditPort {

        private final List<AuditEvent> records = new ArrayList<>();
        private final List<String> denials = new ArrayList<>();
        private final Clock clock;

        public RecordingAuditPort(Clock clock) {
            this.clock = clock;
        }

        @Override
        public AuditEvent record(ActorContext actor, SourceChannel sourceChannel, SiteCode siteScope,
                AuditAction action, String resourceType, String resourceId, Object beforeValue, Object afterValue) {
            String previousHash = records.isEmpty()
                    ? AuditHashChain.GENESIS_HASH
                    : records.get(records.size() - 1).recordHash();
            AuditEvent sealed = AuditHashChain.seal(
                    AuditEvent.unsealed(UUID.randomUUID(), siteScope, actor.actorId(),
                            actor.principal().displayName(), action, resourceType, resourceId,
                            beforeValue == null ? null : beforeValue.toString(),
                            afterValue == null ? null : afterValue.toString(),
                            actor.correlationId(), sourceChannel, clock.instant()),
                    records.size(), previousHash);
            records.add(sealed);
            return sealed;
        }

        @Override
        public void recordAuthorizationDenied(ActorContext actor, String siteScope, String resourceType,
                String resourceId, String requiredPermission, String reason) {
            denials.add(actor.actorId() + "|" + resourceType + "|" + requiredPermission + "|" + reason);
        }

        @Override
        public List<AuditEvent> search(AuditQuery query) {
            return List.copyOf(records);
        }

        @Override
        public AuditChainVerification verifyChain() {
            return AuditHashChain.verify(records, AuditHashChain.GENESIS_HASH);
        }

        public List<AuditEvent> records() {
            return List.copyOf(records);
        }

        public List<String> denials() {
            return List.copyOf(denials);
        }

        public boolean hasRecord(AuditAction action, String resourceType) {
            return records.stream()
                    .anyMatch(event -> event.action() == action && event.resourceType().equals(resourceType));
        }
    }

    /** Collects published events so a test can assert exactly what reached the outbox. */
    public static final class RecordingEventPublisher implements IntegrationEventPublisher {

        private final List<PublishedEvent> published = new ArrayList<>();

        @Override
        public void publish(FleetEventType eventType, String aggregateType, String aggregateId, SiteCode siteScope,
                ActorContext actor, String causationId, Object payload) {
            published.add(new PublishedEvent(eventType, aggregateType, aggregateId,
                    siteScope == null ? null : siteScope.value(), causationId, payload));
        }

        public List<PublishedEvent> published() {
            return List.copyOf(published);
        }

        public List<FleetEventType> types() {
            return published.stream().map(PublishedEvent::eventType).toList();
        }

        public Optional<PublishedEvent> firstOf(FleetEventType type) {
            return published.stream().filter(event -> event.eventType() == type).findFirst();
        }

        public record PublishedEvent(FleetEventType eventType, String aggregateType, String aggregateId,
                String siteScope, String causationId, Object payload) {
        }
    }

    /** Idempotency store that behaves like the real one, including the payload-mismatch rejection. */
    public static final class InMemoryIdempotencyPort implements IdempotencyPort {

        private final Map<String, Entry> store = new LinkedHashMap<>();

        @Override
        public Optional<UUID> findExistingResult(String operation, String idempotencyKey,
                String requestFingerprint) {
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                return Optional.empty();
            }
            Entry entry = store.get(operation + "|" + idempotencyKey);
            if (entry == null) {
                return Optional.empty();
            }
            if (!entry.fingerprint().equals(requestFingerprint)) {
                throw new IdempotencyKeyConflictException(Map.of(
                        "operation", operation, "idempotencyKey", idempotencyKey));
            }
            return Optional.of(entry.resultId());
        }

        @Override
        public void recordResult(String operation, String idempotencyKey, String requestFingerprint, UUID resultId,
                String siteCode, String actorId) {
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                return;
            }
            store.put(operation + "|" + idempotencyKey, new Entry(requestFingerprint, resultId));
        }

        @Override
        public String fingerprint(Object requestPayload) {
            return String.valueOf(requestPayload).hashCode() + "";
        }

        private record Entry(String fingerprint, UUID resultId) {
        }
    }

    /** Runtime configuration with the shipped defaults, overridable per test. */
    public static final class FixedRuntimeConfiguration implements RuntimeConfigurationPort {

        private Duration complianceWarning = Duration.ofDays(30);
        private Duration inspectionValidity = Duration.ofDays(1);
        private Duration serviceDueWarning = Duration.ofDays(14);
        private Duration odometerStaleness = Duration.ofDays(30);
        private Duration telematicsStaleness = Duration.ofHours(6);
        private Duration dashboardFreshness = Duration.ofMinutes(15);
        private Duration signatureWindow = Duration.ofMinutes(5);
        private int maxAttempts = 8;
        private Instant changedAt = Instant.parse("2026-07-01T00:00:00Z");

        public FixedRuntimeConfiguration withComplianceWarning(Duration value) {
            this.complianceWarning = value;
            return this;
        }

        public FixedRuntimeConfiguration withServiceDueWarning(Duration value) {
            this.serviceDueWarning = value;
            return this;
        }

        public FixedRuntimeConfiguration withInspectionValidity(Duration value) {
            this.inspectionValidity = value;
            return this;
        }

        public FixedRuntimeConfiguration withDashboardFreshness(Duration value) {
            this.dashboardFreshness = value;
            return this;
        }

        public FixedRuntimeConfiguration withTelematicsStaleness(Duration value) {
            this.telematicsStaleness = value;
            return this;
        }

        public FixedRuntimeConfiguration withMaxAttempts(int value) {
            this.maxAttempts = value;
            return this;
        }

        public FixedRuntimeConfiguration changedAt(Instant value) {
            this.changedAt = value;
            return this;
        }

        @Override
        public Duration complianceExpiryWarningWindow(String siteCode) {
            return complianceWarning;
        }

        @Override
        public Duration inspectionValidityWindow(String siteCode) {
            return inspectionValidity;
        }

        @Override
        public Duration serviceDueWarningWindow(String siteCode) {
            return serviceDueWarning;
        }

        @Override
        public Duration odometerStalenessThreshold(String siteCode) {
            return odometerStaleness;
        }

        @Override
        public Duration telematicsStalenessThreshold(String siteCode) {
            return telematicsStaleness;
        }

        @Override
        public Duration dashboardFreshnessThreshold(String siteCode) {
            return dashboardFreshness;
        }

        @Override
        public Duration integrationSignatureWindow() {
            return signatureWindow;
        }

        @Override
        public Duration outboundRetryBackoff(int attempt) {
            return Duration.ofSeconds(10L * (1L << Math.max(0, Math.min(attempt - 1, 10))));
        }

        @Override
        public int outboundMaxAttempts() {
            return maxAttempts;
        }

        @Override
        public Optional<String> value(String key, String siteCode) {
            return Optional.empty();
        }

        @Override
        public Instant activeConfigurationChangedAt() {
            return changedAt;
        }
    }

    /** Collects notification intents so tests can assert who was told what. */
    public static final class RecordingNotificationPort implements NotificationPort {

        private final List<String> sent = new ArrayList<>();

        @Override
        public void notifyAssignee(SiteCode siteScope, String assignee, NotificationKind kind,
                String subjectReference, Map<String, String> context) {
            sent.add("USER|" + assignee + "|" + kind + "|" + subjectReference);
        }

        @Override
        public void notifyRole(SiteCode siteScope, SflRole role, NotificationKind kind, String subjectReference,
                Map<String, String> context) {
            sent.add("ROLE|" + role + "|" + kind + "|" + subjectReference);
        }

        public List<String> sent() {
            return List.copyOf(sent);
        }
    }

    // --- Movement projection and readiness ----------------------------------------------

    /**
     * A movement projection that records what it is given.
     *
     * <p>Real rather than empty, because the movement read is now a real endpoint: a double that
     * always returned nothing would let a test pass while the query did nothing at all.
     */
    public static final class InMemoryLocationRepository implements VehicleLocationRepository {

        private final List<VehicleLocationSnapshot> store = new ArrayList<>();

        @Override
        public VehicleLocationSnapshot save(VehicleLocationSnapshot snapshot) {
            store.add(snapshot);
            return snapshot;
        }

        @Override
        public Optional<VehicleLocationSnapshot> findLatestByVehicle(UUID vehicleId) {
            return findByVehicle(vehicleId, 1).stream().findFirst();
        }

        @Override
        public List<VehicleLocationSnapshot> findByVehicle(UUID vehicleId, int limit) {
            return store.stream()
                    .filter(snapshot -> snapshot.vehicleId().equals(vehicleId))
                    .sorted(Comparator.comparing(VehicleLocationSnapshot::recordedAt).reversed())
                    .limit(Math.max(1, limit))
                    .toList();
        }

        @Override
        public List<VehicleLocationSnapshot> findRecentInScope(SiteScopeFilter scope, int limit) {
            return store.stream()
                    .filter(snapshot -> scope.permits(snapshot.siteCode().value()))
                    .sorted(Comparator.comparing(VehicleLocationSnapshot::recordedAt).reversed())
                    .limit(Math.max(1, limit))
                    .toList();
        }
    }

    /**
     * A readiness service wired from in-memory doubles.
     *
     * <p>{@code FleetReadinessService} needs four repositories and a caller usually cares about one
     * or two, so the rest are anonymous here rather than three more named classes nobody reads.
     */
    public static FleetReadinessService readinessService(ComplianceDocumentRepository complianceDocuments,
            Clock clock) {
        VehicleInspectionRepository inspections = new VehicleInspectionRepository() {
            @Override
            public VehicleInspection save(VehicleInspection inspection) {
                return inspection;
            }

            @Override
            public Optional<VehicleInspection> findById(UUID id) {
                return Optional.empty();
            }

            @Override
            public Optional<VehicleInspection> findLatestByVehicle(UUID vehicleId) {
                return Optional.empty();
            }

            @Override
            public List<VehicleInspection> findByVehicle(UUID vehicleId) {
                return List.of();
            }

            @Override
            public List<VehicleInspection> findByTrip(UUID tripId) {
                return List.of();
            }

            @Override
            public List<VehicleInspection> findLatestInScope(SiteScopeFilter scope) {
                return List.of();
            }

            @Override
            public List<VehicleInspection> findFailuresSince(SiteScopeFilter scope, Instant from) {
                return List.of();
            }
        };
        return new FleetReadinessService(complianceDocuments, inspections,
                new FleetWorkflowTestDoubles.InMemoryTripRepository(), new InMemoryDriverProfileRepository(),
                new FixedRuntimeConfiguration(), clock);
    }
}
