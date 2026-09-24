package gh.edu.clet.sfl.safetysecurity.accesscontrol.support;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.application.port.AccessControlRepository;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.application.port.AccessControlVendorGatewayPort;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.application.port.AccessControlIncidentSeedingPort;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.application.port.SiemForwarderPort;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessEvent;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessEventKind;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessException;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessOverride;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessProvisioning;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessZone;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.ExceptionStatus;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.OverrideStatus;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.ProvisioningStatus;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.ReaderHealth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** In-memory {@link AccessControlRepository} for S160a application-layer unit tests - a real
 * implementation of the port's contract, following {@code VisitorTestDoubles}'s idiom. */
public final class AccessControlTestDoubles {

    private AccessControlTestDoubles() {
    }

    public static ActorContext actor(String subject, SflRole role, String... sites) {
        return new ActorContext(new SiteScopedPrincipal(subject, subject, Set.of(role), Set.of(sites), false),
                "corr-test");
    }

    public static final class InMemoryAccessControlRepository implements AccessControlRepository {

        private final Map<UUID, AccessEvent> events = new LinkedHashMap<>();
        private final Map<String, ReaderHealth> readerHealth = new LinkedHashMap<>();
        private final Map<UUID, AccessZone> zones = new LinkedHashMap<>();
        private final Map<UUID, AccessProvisioning> provisioning = new LinkedHashMap<>();
        private final Map<UUID, AccessOverride> overrides = new LinkedHashMap<>();
        private final Map<UUID, AccessException> exceptions = new LinkedHashMap<>();

        @Override
        public AccessEvent saveEvent(AccessEvent event) {
            events.put(event.id(), event);
            return event;
        }

        @Override
        public Optional<AccessEvent> findEventByExternalId(String source, String externalEventId) {
            return events.values().stream()
                    .filter(e -> e.source().equals(source) && e.externalEventId().equals(externalEventId))
                    .findFirst();
        }

        @Override
        public List<AccessEvent> findRecentEvents(String siteCode, String zoneCode, String personRef, int limit) {
            return events.values().stream()
                    .filter(e -> e.siteCode().equalsIgnoreCase(siteCode) && e.zoneCode().equals(zoneCode))
                    .filter(e -> personRef == null || personRef.equals(e.personRef()))
                    .sorted(Comparator.comparing(AccessEvent::occurredAt).reversed())
                    .limit(Math.max(1, limit))
                    .toList();
        }

        @Override
        public Optional<AccessEvent> findLastGrantedEvent(String siteCode, String zoneCode, String personRef) {
            return events.values().stream()
                    .filter(e -> e.siteCode().equalsIgnoreCase(siteCode) && e.zoneCode().equals(zoneCode)
                            && personRef.equals(e.personRef()) && e.kind() == AccessEventKind.GRANTED)
                    .max(Comparator.comparing(AccessEvent::occurredAt));
        }

        @Override
        public ReaderHealth saveReaderHealth(ReaderHealth health) {
            readerHealth.put(health.siteCode() + ":" + health.readerId(), health);
            return health;
        }

        @Override
        public Optional<ReaderHealth> findReaderHealth(String siteCode, String readerId) {
            return Optional.ofNullable(readerHealth.get(siteCode + ":" + readerId));
        }

        @Override
        public List<ReaderHealth> findReaderHealthBySite(String siteCode) {
            return readerHealth.values().stream().filter(h -> h.siteCode().equalsIgnoreCase(siteCode)).toList();
        }

        @Override
        public AccessZone saveZone(AccessZone zone) {
            zones.put(zone.id(), zone);
            return zone;
        }

        @Override
        public Optional<AccessZone> findZone(UUID id) {
            return Optional.ofNullable(zones.get(id));
        }

        @Override
        public Optional<AccessZone> findZoneByCode(String siteCode, String zoneCode) {
            return zones.values().stream()
                    .filter(z -> z.siteCode().equalsIgnoreCase(siteCode) && z.zoneCode().equals(zoneCode))
                    .findFirst();
        }

        @Override
        public List<AccessZone> findZonesBySite(String siteCode) {
            return zones.values().stream().filter(z -> z.siteCode().equalsIgnoreCase(siteCode)).toList();
        }

        @Override
        public AccessProvisioning saveProvisioning(AccessProvisioning provisioningRecord) {
            provisioning.put(provisioningRecord.id(), provisioningRecord);
            return provisioningRecord;
        }

        @Override
        public Optional<AccessProvisioning> findProvisioning(UUID id) {
            return Optional.ofNullable(provisioning.get(id));
        }

        @Override
        public List<AccessProvisioning> findProvisioningForPerson(String siteCode, String personRef) {
            return provisioning.values().stream()
                    .filter(p -> p.siteCode().equalsIgnoreCase(siteCode) && p.personRef().equals(personRef))
                    .toList();
        }

        @Override
        public List<AccessProvisioning> findProvisioningByStatus(String siteCode, ProvisioningStatus status) {
            return provisioning.values().stream()
                    .filter(p -> p.siteCode().equalsIgnoreCase(siteCode) && p.status() == status)
                    .toList();
        }

        @Override
        public AccessOverride saveOverride(AccessOverride override) {
            overrides.put(override.id(), override);
            return override;
        }

        @Override
        public Optional<AccessOverride> findOverride(UUID id) {
            return Optional.ofNullable(overrides.get(id));
        }

        @Override
        public List<AccessOverride> findOverridesByStatus(String siteCode, OverrideStatus status) {
            return overrides.values().stream()
                    .filter(o -> o.siteCode().equalsIgnoreCase(siteCode) && o.status() == status)
                    .toList();
        }

        @Override
        public List<AccessOverride> findActiveOverrides() {
            return overrides.values().stream().filter(o -> o.status() == OverrideStatus.ACTIVE).toList();
        }

        @Override
        public AccessException saveException(AccessException exception) {
            exceptions.put(exception.id(), exception);
            return exception;
        }

        @Override
        public Optional<AccessException> findException(UUID id) {
            return Optional.ofNullable(exceptions.get(id));
        }

        @Override
        public List<AccessException> findExceptionsByStatus(String siteCode, ExceptionStatus status) {
            return exceptions.values().stream()
                    .filter(e -> e.siteCode().equalsIgnoreCase(siteCode) && e.status() == status)
                    .toList();
        }
    }

    /** Records what it would have pushed to the vendor system - never fabricates success, matching
     * {@code RecordedAccessControlVendorGateway}'s real behaviour. */
    public static final class FakeVendorGateway implements AccessControlVendorGatewayPort {
        public final List<Object> synced = new java.util.ArrayList<>();

        @Override
        public SyncResult syncZone(gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessZone zone,
                ActorContext actor) {
            synced.add(zone);
            return new SyncResult("FAKE", false);
        }

        @Override
        public SyncResult syncOverride(AccessOverride override, ActorContext actor) {
            synced.add(override);
            return new SyncResult("FAKE", false);
        }

        @Override
        public SyncResult syncOverrideReversion(UUID overrideId, ActorContext actor) {
            synced.add(overrideId);
            return new SyncResult("FAKE", false);
        }
    }

    public static final class FakeSiemForwarder implements SiemForwarderPort {
        public final List<AccessException> forwarded = new java.util.ArrayList<>();

        @Override
        public ForwardResult forward(AccessException exception, ActorContext actor) {
            forwarded.add(exception);
            return new ForwardResult("FAKE", true);
        }
    }

    public static final class FakeAccessControlIncidentSeedingPort implements AccessControlIncidentSeedingPort {
        public final List<String> descriptions = new java.util.ArrayList<>();
        public UUID nextIncidentId = UUID.randomUUID();

        @Override
        public UUID seed(String siteCode, String description, ActorContext actor) {
            descriptions.add(description);
            return nextIncidentId;
        }
    }

    public static final class FakeAuditPort
            implements gh.edu.clet.sfl.safetysecurity.platform.application.port.AuditPort {
        public int recordCalls;

        @Override
        public void record(ActorContext actor, String sourceChannel, String siteScope, String action,
                String resourceType, String resourceId, Object beforeValue, Object afterValue, String reason) {
            recordCalls++;
        }

        @Override
        public AuditVerification verifyChain() {
            return new AuditVerification(true, recordCalls, null, null);
        }
    }

    public static final class FakeEventPublisher
            implements gh.edu.clet.sfl.safetysecurity.platform.application.port.IntegrationEventPublisher {
        public int publishCalls;

        @Override
        public void publish(String eventType, int eventVersion, String aggregateType, String aggregateId,
                String siteScope, ActorContext actor, Map<String, Object> payload) {
            publishCalls++;
        }
    }
}
