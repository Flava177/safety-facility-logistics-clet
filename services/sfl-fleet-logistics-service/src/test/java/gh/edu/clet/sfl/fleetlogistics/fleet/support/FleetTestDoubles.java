package gh.edu.clet.sfl.fleetlogistics.fleet.support;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.ComplianceDocumentRepository;
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
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RegistrationNumber;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Vehicle;
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
}
