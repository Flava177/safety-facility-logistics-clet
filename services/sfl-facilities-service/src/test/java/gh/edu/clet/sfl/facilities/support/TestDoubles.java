package gh.edu.clet.sfl.facilities.support;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.facilities.dashboard.application.ports.MaintenanceReadModel;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.IdempotencyPort;
import gh.edu.clet.sfl.facilities.shared.application.port.RuntimeConfigurationPort;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** The small collaborators the S152 application tests need. */
public final class TestDoubles {

    private TestDoubles() {
    }

    /** An actor with the given roles, scoped to the given sites. {@code *} means every site. */
    public static ActorContext actor(String id, Set<SflRole> roles, String... sites) {
        return new ActorContext(new SiteScopedPrincipal(id, id, roles, Set.of(sites), false),
                "corr-" + id);
    }

    /** Records the events an operation publishes, so a test can assert on the outbox. */
    public static final class RecordingOutbox implements ServiceOutbox {

        public record Recorded(String eventType, String aggregateType, UUID aggregateId, String siteScope,
                Object payload) {
        }

        private final List<Recorded> recorded = new ArrayList<>();

        public List<Recorded> events() {
            return List.copyOf(recorded);
        }

        public List<String> eventTypes() {
            return recorded.stream().map(Recorded::eventType).toList();
        }

        public boolean published(String eventType) {
            return recorded.stream().anyMatch(event -> event.eventType().equals(eventType));
        }

        public void clear() {
            recorded.clear();
        }

        @Override
        public void record(String eventType, int eventVersion, String aggregateType, UUID aggregateId,
                String siteScope, String correlationId, String causationId, Object payload) {
            recorded.add(new Recorded(eventType, aggregateType, aggregateId, siteScope, payload));
        }
    }

    /**
     * An idempotency store keyed on operation and key, enforcing the fingerprint rule.
     *
     * <p>Fingerprints by {@code toString} rather than by JSON: the commands' payloads are already
     * strings, and pulling an ObjectMapper in would test Jackson rather than the rule.
     */
    public static final class InMemoryIdempotency implements IdempotencyPort {

        private record Entry(String fingerprint, UUID resultId) {
        }

        private final Map<String, Entry> entries = new HashMap<>();

        @Override
        public Optional<UUID> findExistingResult(String operation, String idempotencyKey,
                String requestFingerprint) {
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                return Optional.empty();
            }
            Entry existing = entries.get(operation + "|" + idempotencyKey);
            if (existing == null) {
                return Optional.empty();
            }
            if (!existing.fingerprint().equals(requestFingerprint)) {
                throw new FacilitiesException.IdempotencyKeyConflictException(operation, idempotencyKey);
            }
            return Optional.of(existing.resultId());
        }

        @Override
        public void recordResult(String operation, String idempotencyKey, String requestFingerprint,
                UUID resultId, String siteCode, String actorId) {
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                return;
            }
            entries.put(operation + "|" + idempotencyKey, new Entry(requestFingerprint, resultId));
        }

        @Override
        public String fingerprint(Object requestPayload) {
            return String.valueOf(requestPayload);
        }
    }

    /** Runtime configuration backed by a map, so a test can move a threshold. */
    public static final class InMemoryConfiguration implements RuntimeConfigurationPort {

        private final Map<String, String> values = new HashMap<>();

        public InMemoryConfiguration set(String key, String value) {
            values.put(key, value);
            return this;
        }

        @Override
        public Optional<String> find(String key, String siteCode) {
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public Duration duration(String key, String siteCode, Duration fallback) {
            return find(key, siteCode).map(Duration::parse).orElse(fallback);
        }

        @Override
        public int integer(String key, String siteCode, int fallback) {
            return find(key, siteCode).map(Integer::parseInt).orElse(fallback);
        }

        @Override
        public List<ConfigurationValue> activeValues(String siteCode) {
            return values.entrySet().stream()
                    .map(entry -> new ConfigurationValue(entry.getKey(), null, entry.getValue(), "STRING",
                            null, 0L, "test", Instant.EPOCH))
                    .toList();
        }

        @Override
        public ConfigurationValue put(String key, String siteCode, String value, String valueType,
                String description, String actorId) {
            values.put(key, value);
            return new ConfigurationValue(key, siteCode, value, valueType, description, 0L, actorId,
                    Instant.EPOCH);
        }
    }

    /** Maintenance counts a test can set directly. */
    public static final class StubMaintenance implements MaintenanceReadModel {

        private OpenWork openWork = new OpenWork(0, 0);
        private final Set<String> locations = new HashSet<>();

        public StubMaintenance withOpenWork(int faults, int workOrders) {
            this.openWork = new OpenWork(faults, workOrders);
            return this;
        }

        public StubMaintenance withOpenWorkAt(String locationCode) {
            locations.add(locationCode);
            return this;
        }

        @Override
        public OpenWork openWorkFor(String siteCode) {
            return openWork;
        }

        @Override
        public Set<String> locationCodesWithOpenWork(String siteCode) {
            return Set.copyOf(locations);
        }
    }
}
