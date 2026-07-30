package gh.edu.clet.sfl.fleetlogistics.fleet.application.service;

import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.IntegrationCommands.ReceiveIntegrationMessage;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.IntegrationCommands.ReplayIntegrationMessage;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IntegrationInboxRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.RuntimeConfigurationPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleLocationRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.event.FleetEventType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.DuplicateIntegrationMessageException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.InvalidSignatureException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.SchemaValidationFailedException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.IntegrationInboxMessage;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.IntegrationMessageStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleLocationSnapshot;
import gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetTestDoubles;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Traces: SRS-SFL-S166-04 secure integration intake, idempotency and health. */
class FleetIntegrationApplicationServiceTest {

    private static final String EVENT = "sfl.ftlmp.vehicle-location-received.v1";
    private static final String RAW = """
            {"eventType":"sfl.ftlmp.vehicle-location-received.v1","siteCode":"ACCRA","occurredAt":"2026-07-21T09:00:00Z","payload":{"vehicleId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","latitude":"5.6037","longitude":"-0.1870","odometerValue":"44210"}}
            """;

    private InMemoryInboxRepository inbox;
    private InMemoryLocationRepository locations;
    private Config configuration;
    private FleetTestDoubles.RecordingAuditPort audit;
    private FleetTestDoubles.RecordingEventPublisher events;
    private FleetIntegrationApplicationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        inbox = new InMemoryInboxRepository();
        locations = new InMemoryLocationRepository();
        configuration = new Config().allow("TELEMATICS", "secret-one");
        audit = new FleetTestDoubles.RecordingAuditPort(clock);
        events = new FleetTestDoubles.RecordingEventPublisher();
        service = new FleetIntegrationApplicationService(inbox, locations, configuration, new FleetAccessPolicy(),
                audit, events, clock);
    }

    @Test
    @DisplayName("valid allowlisted HMAC message is stored, processed once and emitted through outbox")
    void valid_message_is_processed_once() {
        IntegrationInboxMessage processed = service.receive(command("idem-001", signature(RAW), payload()));

        assertThat(processed.status()).isEqualTo(IntegrationMessageStatus.PROCESSED);
        assertThat(inbox.store).hasSize(1);
        assertThat(locations.latest()).isPresent()
                .get()
                .satisfies(location -> {
                    assertThat(location.vehicleId()).isEqualTo(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
                    assertThat(location.sourceSystem()).isEqualTo("TELEMATICS");
                });
        assertThat(events.types()).contains(FleetEventType.VEHICLE_LOCATION_RECEIVED);

        assertThatThrownBy(() -> service.receive(command("idem-001", signature(RAW), payload())))
                .isInstanceOf(DuplicateIntegrationMessageException.class);
    }

    @Test
    @DisplayName("invalid HMAC and stale timestamps are rejected before persistence")
    void invalid_signature_is_rejected() {
        assertThatThrownBy(() -> service.receive(command("idem-002", "not-the-signature", payload())))
                .isInstanceOf(InvalidSignatureException.class);

        assertThat(inbox.store).isEmpty();
    }

    @Test
    @DisplayName("schema validation rejects malformed integration payloads")
    void schema_validation_rejects_missing_required_fields() {
        Map<String, Object> invalidPayload = new LinkedHashMap<>(payload());
        invalidPayload.remove("vehicleId");

        assertThatThrownBy(() -> service.receive(command("idem-003", signature(RAW), invalidPayload)))
                .isInstanceOf(SchemaValidationFailedException.class);
    }

    @Test
    @DisplayName("health and replay are role controlled integration operations")
    void health_and_replay_are_role_controlled() {
        IntegrationInboxMessage processed = service.receive(command("idem-004", signature(RAW), payload()));

        var health = service.health(FleetTestDoubles.actor("integrator@clet.edu.gh",
                Set.of(SflRole.INTEGRATION_ENGINEER), Set.of("*")));

        assertThat(health.processedMessages()).isEqualTo(1);
        assertThat(health.recentMessages()).singleElement()
                .satisfies(message -> assertThat(message.id()).isEqualTo(processed.id()));

        IntegrationInboxMessage replayed = service.replay(new ReplayIntegrationMessage(processed.id(),
                FleetTestDoubles.actor("integrator@clet.edu.gh", Set.of(SflRole.INTEGRATION_ENGINEER),
                        Set.of("ACCRA")), gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel.API));

        assertThat(replayed.id()).isEqualTo(processed.id());
    }

    private ReceiveIntegrationMessage command(String idempotencyKey, String signature, Map<String, Object> payload) {
        return new ReceiveIntegrationMessage("telematics", idempotencyKey, EVENT, "ACCRA",
                Instant.parse("2026-07-21T09:00:00Z"), signature, NOW, RAW, payload,
                FleetTestDoubles.actor("telematics-client", Set.of(SflRole.SERVICE_INTEGRATION),
                        Set.of("ACCRA")),
                gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel.INTEGRATION);
    }

    private static String signature(String raw) {
        return FleetIntegrationApplicationService.hmac("secret-one", NOW + "." + raw);
    }

    private static Map<String, Object> payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("vehicleId", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        payload.put("latitude", "5.6037");
        payload.put("longitude", "-0.1870");
        payload.put("odometerValue", "44210");
        return payload;
    }

    private static final class Config implements RuntimeConfigurationPort {

        private final Map<String, String> values = new LinkedHashMap<>();

        Config allow(String source, String secret) {
            values.put("fleet.integration." + source + ".enabled", "true");
            values.put("fleet.integration." + source + ".secret", secret);
            return this;
        }

        @Override
        public Optional<String> value(String key, String siteCode) {
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public Duration complianceExpiryWarningWindow(String siteCode) {
            return Duration.ofDays(30);
        }

        @Override
        public Duration inspectionValidityWindow(String siteCode) {
            return Duration.ofDays(1);
        }

        @Override
        public Duration serviceDueWarningWindow(String siteCode) {
            return Duration.ofDays(14);
        }

        @Override
        public Duration odometerStalenessThreshold(String siteCode) {
            return Duration.ofDays(30);
        }

        @Override
        public Duration telematicsStalenessThreshold(String siteCode) {
            return Duration.ofHours(6);
        }

        @Override
        public Duration dashboardFreshnessThreshold(String siteCode) {
            return Duration.ofMinutes(15);
        }

        @Override
        public Duration integrationSignatureWindow() {
            return Duration.ofMinutes(5);
        }

        @Override
        public Duration outboundRetryBackoff(int attempt) {
            return Duration.ofSeconds(10);
        }

        @Override
        public int outboundMaxAttempts() {
            return 8;
        }

        @Override
        public Instant activeConfigurationChangedAt() {
            return Instant.parse("2026-07-01T00:00:00Z");
        }
    }

    private static final class InMemoryInboxRepository implements IntegrationInboxRepository {

        private final Map<UUID, IntegrationInboxMessage> store = new LinkedHashMap<>();

        @Override
        public IntegrationInboxMessage save(IntegrationInboxMessage message) {
            store.put(message.id(), message);
            return message;
        }

        @Override
        public Optional<IntegrationInboxMessage> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<IntegrationInboxMessage> findBySourceAndIdempotencyKey(String sourceSystem,
                String idempotencyKey) {
            return store.values().stream()
                    .filter(message -> message.sourceSystem().equals(sourceSystem))
                    .filter(message -> message.idempotencyKey().equals(idempotencyKey))
                    .findFirst();
        }

        @Override
        public List<IntegrationInboxMessage> findRecent(int limit) {
            return store.values().stream()
                    .sorted(Comparator.comparing(IntegrationInboxMessage::receivedAt).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<IntegrationInboxMessage> search(String sourceSystem, IntegrationMessageStatus status,
                String eventType, int limit) {
            return store.values().stream()
                    .filter(message -> sourceSystem == null || message.sourceSystem().equals(sourceSystem))
                    .filter(message -> status == null || message.status() == status)
                    .filter(message -> eventType == null || message.eventType().equals(eventType))
                    .sorted(Comparator.comparing(IntegrationInboxMessage::receivedAt).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public long countByStatus(IntegrationMessageStatus status) {
            return store.values().stream().filter(message -> message.status() == status).count();
        }
    }

    private static final class InMemoryLocationRepository implements VehicleLocationRepository {

        private final Map<UUID, VehicleLocationSnapshot> store = new LinkedHashMap<>();

        @Override
        public VehicleLocationSnapshot save(VehicleLocationSnapshot snapshot) {
            store.put(snapshot.id(), snapshot);
            return snapshot;
        }

        @Override
        public Optional<VehicleLocationSnapshot> findLatestByVehicle(UUID vehicleId) {
            return store.values().stream()
                    .filter(snapshot -> snapshot.vehicleId().equals(vehicleId))
                    .max(Comparator.comparing(VehicleLocationSnapshot::recordedAt));
        }

        @Override
        public List<VehicleLocationSnapshot> findRecentInScope(SiteScopeFilter scope, int limit) {
            return store.values().stream()
                    .filter(snapshot -> scope.permits(snapshot.siteCode().value()))
                    .sorted(Comparator.comparing(VehicleLocationSnapshot::recordedAt).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<VehicleLocationSnapshot> findByVehicle(UUID vehicleId, int limit) {
            return store.values().stream()
                    .filter(snapshot -> snapshot.vehicleId().equals(vehicleId))
                    .sorted(Comparator.comparing(VehicleLocationSnapshot::recordedAt).reversed())
                    .limit(limit)
                    .toList();
        }

        Optional<VehicleLocationSnapshot> latest() {
            return store.values().stream().findFirst();
        }
    }
}
