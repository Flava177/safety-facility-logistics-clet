package gh.edu.clet.sfl.safetysecurity.intrusion.infrastructure.persistence;

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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** The one adapter behind {@link IntrusionRepository}, following {@code AccessControlRepositoryAdapter}'s shape. */
@Repository
public class IntrusionRepositoryAdapter implements IntrusionRepository {

    private final IntrusionSignalJpaRepository signals;
    private final IntrusionAlarmJpaRepository alarms;
    private final PanelHealthJpaRepository panelHealth;
    private final IntrusionZoneJpaRepository zones;
    private final DisarmOverrideJpaRepository disarmOverrides;
    private final ResponseDispatchJpaRepository dispatches;

    public IntrusionRepositoryAdapter(IntrusionSignalJpaRepository signals, IntrusionAlarmJpaRepository alarms,
            PanelHealthJpaRepository panelHealth, IntrusionZoneJpaRepository zones,
            DisarmOverrideJpaRepository disarmOverrides, ResponseDispatchJpaRepository dispatches) {
        this.signals = signals;
        this.alarms = alarms;
        this.panelHealth = panelHealth;
        this.zones = zones;
        this.disarmOverrides = disarmOverrides;
        this.dispatches = dispatches;
    }

    @Override
    public IntrusionSignal saveSignal(IntrusionSignal signal) {
        return signals.save(IntrusionSignalJpaEntity.from(signal)).toDomain();
    }

    @Override
    public Optional<IntrusionSignal> findSignalByExternalId(String source, String externalEventId) {
        return signals.findBySourceAndExternalEventId(source, externalEventId).map(IntrusionSignalJpaEntity::toDomain);
    }

    @Override
    public IntrusionAlarm saveAlarm(IntrusionAlarm alarm) {
        IntrusionAlarmJpaEntity entity = alarms.findById(alarm.id()).orElseGet(IntrusionAlarmJpaEntity::new);
        entity.apply(alarm);
        return alarms.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<IntrusionAlarm> findAlarm(UUID id) {
        return alarms.findById(id).map(IntrusionAlarmJpaEntity::toDomain);
    }

    @Override
    public Optional<IntrusionAlarm> findOpenAlarm(String siteCode, String panelId, String zoneCode,
            AlarmType alarmType) {
        return alarms.findOpen(siteCode, panelId, zoneCode, alarmType).map(IntrusionAlarmJpaEntity::toDomain);
    }

    @Override
    public List<IntrusionAlarm> findAlarmsByStatus(String siteCode, AlarmStatus status) {
        return alarms.findBySiteCodeAndStatus(siteCode, status).stream().map(IntrusionAlarmJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<IntrusionAlarm> findRaisedAlarms() {
        return alarms.findAllRaised().stream().map(IntrusionAlarmJpaEntity::toDomain).toList();
    }

    @Override
    public long countByOutcome(String siteCode, DispatchOutcome outcome) {
        return dispatches.countBySiteCodeAndOutcome(siteCode, outcome);
    }

    @Override
    public PanelHealth savePanelHealth(PanelHealth health) {
        PanelHealthJpaEntity entity = panelHealth.findById(health.id()).orElseGet(PanelHealthJpaEntity::new);
        entity.apply(health);
        return panelHealth.save(entity).toDomain();
    }

    @Override
    public Optional<PanelHealth> findPanelHealth(String siteCode, String panelId) {
        return panelHealth.findBySiteCodeAndPanelId(siteCode, panelId).map(PanelHealthJpaEntity::toDomain);
    }

    @Override
    public IntrusionZone saveZone(IntrusionZone zone) {
        IntrusionZoneJpaEntity entity = zones.findById(zone.id()).orElseGet(IntrusionZoneJpaEntity::new);
        entity.apply(zone);
        return zones.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<IntrusionZone> findZone(UUID id) {
        return zones.findById(id).map(IntrusionZoneJpaEntity::toDomain);
    }

    @Override
    public Optional<IntrusionZone> findZoneByCode(String siteCode, String zoneCode) {
        return zones.findBySiteCodeAndZoneCode(siteCode, zoneCode).map(IntrusionZoneJpaEntity::toDomain);
    }

    @Override
    public List<IntrusionZone> findZonesBySite(String siteCode) {
        return zones.findBySiteCode(siteCode).stream().map(IntrusionZoneJpaEntity::toDomain).toList();
    }

    @Override
    public DisarmOverride saveDisarmOverride(DisarmOverride override) {
        DisarmOverrideJpaEntity entity = disarmOverrides.findById(override.id())
                .orElseGet(DisarmOverrideJpaEntity::new);
        entity.apply(override);
        return disarmOverrides.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<DisarmOverride> findDisarmOverride(UUID id) {
        return disarmOverrides.findById(id).map(DisarmOverrideJpaEntity::toDomain);
    }

    @Override
    public List<DisarmOverride> findDisarmOverridesByStatus(String siteCode, DisarmOverrideStatus status) {
        return disarmOverrides.findBySiteCodeAndStatus(siteCode, status).stream()
                .map(DisarmOverrideJpaEntity::toDomain).toList();
    }

    @Override
    public List<DisarmOverride> findActiveDisarmOverrides() {
        return disarmOverrides.findByStatus(DisarmOverrideStatus.ACTIVE).stream()
                .map(DisarmOverrideJpaEntity::toDomain).toList();
    }

    @Override
    public ResponseDispatch saveDispatch(ResponseDispatch dispatch) {
        ResponseDispatchJpaEntity entity = dispatches.findById(dispatch.id()).orElseGet(ResponseDispatchJpaEntity::new);
        entity.apply(dispatch);
        return dispatches.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<ResponseDispatch> findDispatch(UUID id) {
        return dispatches.findById(id).map(ResponseDispatchJpaEntity::toDomain);
    }

    @Override
    public Optional<ResponseDispatch> findDispatchByAlarm(UUID alarmId) {
        return dispatches.findByAlarmId(alarmId).map(ResponseDispatchJpaEntity::toDomain);
    }
}
