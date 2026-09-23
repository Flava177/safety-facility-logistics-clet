package gh.edu.clet.sfl.safetysecurity.intrusion.support;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.safetysecurity.intrusion.application.port.ArmedResponseCoordinationPort;
import gh.edu.clet.sfl.safetysecurity.intrusion.application.port.IntrusionIncidentSeedingPort;
import gh.edu.clet.sfl.safetysecurity.intrusion.application.port.IntrusionPanelGatewayPort;
import gh.edu.clet.sfl.safetysecurity.intrusion.application.port.IntrusionRepository;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.AlarmStatus;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.AlarmType;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.DisarmOverride;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.DisarmOverrideStatus;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.DispatchOutcome;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.IntrusionAlarm;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.IntrusionSignal;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.IntrusionZone;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.PanelHealth;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.ResponseDispatch;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** In-memory {@link IntrusionRepository} for S162 application-layer unit tests - a real
 * implementation of the port's contract, following {@code AccessControlTestDoubles}'s idiom. */
public final class IntrusionTestDoubles {

    private IntrusionTestDoubles() {
    }

    public static ActorContext actor(String subject, SflRole role, String... sites) {
        return new ActorContext(new SiteScopedPrincipal(subject, subject, Set.of(role), Set.of(sites), false),
                "corr-test");
    }

    public static final class InMemoryIntrusionRepository implements IntrusionRepository {

        private final Map<UUID, IntrusionSignal> signals = new LinkedHashMap<>();
        private final Map<UUID, IntrusionAlarm> alarms = new LinkedHashMap<>();
        private final Map<String, PanelHealth> panelHealth = new LinkedHashMap<>();
        private final Map<UUID, IntrusionZone> zones = new LinkedHashMap<>();
        private final Map<UUID, DisarmOverride> disarmOverrides = new LinkedHashMap<>();
        private final Map<UUID, ResponseDispatch> dispatches = new LinkedHashMap<>();

        @Override
        public IntrusionSignal saveSignal(IntrusionSignal signal) {
            signals.put(signal.id(), signal);
            return signal;
        }

        @Override
        public Optional<IntrusionSignal> findSignalByExternalId(String source, String externalEventId) {
            return signals.values().stream()
                    .filter(s -> s.source().equals(source) && s.externalEventId().equals(externalEventId))
                    .findFirst();
        }

        @Override
        public IntrusionAlarm saveAlarm(IntrusionAlarm alarm) {
            alarms.put(alarm.id(), alarm);
            return alarm;
        }

        @Override
        public Optional<IntrusionAlarm> findAlarm(UUID id) {
            return Optional.ofNullable(alarms.get(id));
        }

        @Override
        public Optional<IntrusionAlarm> findOpenAlarm(String siteCode, String panelId, String zoneCode,
                AlarmType alarmType) {
            return alarms.values().stream()
                    .filter(a -> a.siteCode().equalsIgnoreCase(siteCode) && a.panelId().equals(panelId)
                            && a.zoneCode().equals(zoneCode) && a.alarmType() == alarmType && a.isOpen())
                    .findFirst();
        }

        @Override
        public List<IntrusionAlarm> findAlarmsByStatus(String siteCode, AlarmStatus status) {
            return alarms.values().stream()
                    .filter(a -> a.siteCode().equalsIgnoreCase(siteCode) && a.status() == status).toList();
        }

        @Override
        public List<IntrusionAlarm> findRaisedAlarms() {
            return alarms.values().stream().filter(a -> a.status() == AlarmStatus.RAISED).toList();
        }

        @Override
        public long countByOutcome(String siteCode, DispatchOutcome outcome) {
            return dispatches.values().stream()
                    .filter(d -> d.siteCode().equalsIgnoreCase(siteCode) && d.outcome() == outcome).count();
        }

        @Override
        public PanelHealth savePanelHealth(PanelHealth health) {
            panelHealth.put(health.siteCode() + ":" + health.panelId(), health);
            return health;
        }

        @Override
        public Optional<PanelHealth> findPanelHealth(String siteCode, String panelId) {
            return Optional.ofNullable(panelHealth.get(siteCode + ":" + panelId));
        }

        @Override
        public IntrusionZone saveZone(IntrusionZone zone) {
            zones.put(zone.id(), zone);
            return zone;
        }

        @Override
        public Optional<IntrusionZone> findZone(UUID id) {
            return Optional.ofNullable(zones.get(id));
        }

        @Override
        public Optional<IntrusionZone> findZoneByCode(String siteCode, String zoneCode) {
            return zones.values().stream()
                    .filter(z -> z.siteCode().equalsIgnoreCase(siteCode) && z.zoneCode().equals(zoneCode))
                    .findFirst();
        }

        @Override
        public List<IntrusionZone> findZonesBySite(String siteCode) {
            return zones.values().stream().filter(z -> z.siteCode().equalsIgnoreCase(siteCode)).toList();
        }

        @Override
        public DisarmOverride saveDisarmOverride(DisarmOverride override) {
            disarmOverrides.put(override.id(), override);
            return override;
        }

        @Override
        public Optional<DisarmOverride> findDisarmOverride(UUID id) {
            return Optional.ofNullable(disarmOverrides.get(id));
        }

        @Override
        public List<DisarmOverride> findDisarmOverridesByStatus(String siteCode, DisarmOverrideStatus status) {
            return disarmOverrides.values().stream()
                    .filter(o -> o.siteCode().equalsIgnoreCase(siteCode) && o.status() == status).toList();
        }

        @Override
        public List<DisarmOverride> findActiveDisarmOverrides() {
            return disarmOverrides.values().stream().filter(o -> o.status() == DisarmOverrideStatus.ACTIVE).toList();
        }

        @Override
        public ResponseDispatch saveDispatch(ResponseDispatch dispatch) {
            dispatches.put(dispatch.id(), dispatch);
            return dispatch;
        }

        @Override
        public Optional<ResponseDispatch> findDispatch(UUID id) {
            return Optional.ofNullable(dispatches.get(id));
        }

        @Override
        public Optional<ResponseDispatch> findDispatchByAlarm(UUID alarmId) {
            return dispatches.values().stream().filter(d -> d.alarmId().equals(alarmId)).findFirst();
        }
    }

    /** Records what it would have pushed to the panels - never fabricates success, matching
     * {@code RecordedIntrusionPanelGateway}'s real behaviour. */
    public static final class FakePanelGateway implements IntrusionPanelGatewayPort {
        public final List<Object> synced = new ArrayList<>();

        @Override
        public SyncResult requestDisarm(DisarmOverride override, ActorContext actor) {
            synced.add(override);
            return new SyncResult("FAKE", false);
        }

        @Override
        public SyncResult requestReArm(String siteCode, String zoneCode, ActorContext actor) {
            synced.add(siteCode + ":" + zoneCode);
            return new SyncResult("FAKE", false);
        }
    }

    public static final class FakeArmedResponseGateway implements ArmedResponseCoordinationPort {
        public final List<IntrusionAlarm> dispatched = new ArrayList<>();

        @Override
        public DispatchRequestResult requestDispatch(IntrusionAlarm alarm, ActorContext actor) {
            dispatched.add(alarm);
            return new DispatchRequestResult("FAKE", false);
        }
    }

    public static final class FakeIncidentSeedingPort implements IntrusionIncidentSeedingPort {
        public final List<String> descriptions = new ArrayList<>();
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
