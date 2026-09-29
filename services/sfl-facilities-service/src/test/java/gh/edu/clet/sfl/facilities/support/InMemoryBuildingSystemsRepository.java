package gh.edu.clet.sfl.facilities.support;

import gh.edu.clet.sfl.facilities.buildingsystems.application.ports.BuildingSystemsRepository;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.AlertStatus;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.AlertType;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.AvampAssetProjection;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BmsAlert;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BmsDevice;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.ChannelState;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.DeviceStatus;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.MeasuredQuantity;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.QuarantineStatus;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.QuarantinedReading;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.RuleConflict;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.TelemetryReading;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.ThresholdRule;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** An in-memory {@link BuildingSystemsRepository} for S156 unit and scenario tests. */
public final class InMemoryBuildingSystemsRepository implements BuildingSystemsRepository {

    private final Map<String, AvampAssetProjection> avampAssets = new LinkedHashMap<>();
    private final Map<UUID, BmsDevice> devices = new LinkedHashMap<>();
    private final Map<UUID, ChannelState> channelStates = new LinkedHashMap<>();
    private final Map<UUID, TelemetryReading> readings = new LinkedHashMap<>();
    private final Map<UUID, QuarantinedReading> quarantine = new LinkedHashMap<>();
    private final Map<UUID, ThresholdRule> rules = new LinkedHashMap<>();
    private final Map<UUID, RuleConflict> conflicts = new LinkedHashMap<>();
    private final Map<UUID, BmsAlert> alerts = new LinkedHashMap<>();

    // ---- AVAMP projection ------------------------------------------------------------------------

    @Override
    public Optional<AvampAssetProjection> findAvampAsset(String avampAssetId) {
        return Optional.ofNullable(avampAssets.get(avampAssetId));
    }

    @Override
    public AvampAssetProjection saveAvampAsset(AvampAssetProjection asset) {
        avampAssets.put(asset.avampAssetId(), asset);
        return asset;
    }

    // ---- devices ---------------------------------------------------------------------------------

    @Override
    public BmsDevice saveDevice(BmsDevice device) {
        devices.put(device.id(), device);
        return device;
    }

    @Override
    public Optional<BmsDevice> findDevice(UUID id) {
        return Optional.ofNullable(devices.get(id));
    }

    @Override
    public Optional<BmsDevice> findActiveDeviceByCode(String siteCode, String deviceCode) {
        return devices.values().stream()
                .filter(device -> device.isActive() && device.siteCode().equals(siteCode)
                        && device.deviceCode().equals(deviceCode))
                .findFirst();
    }

    @Override
    public Optional<BmsDevice> findDeviceByAvampAssetId(String avampAssetId) {
        return devices.values().stream().filter(device -> device.avampAssetId().equals(avampAssetId)).findFirst();
    }

    @Override
    public List<BmsDevice> findDevices(String siteCode, DeviceStatus status) {
        return devices.values().stream()
                .filter(device -> siteCode == null || device.siteCode().equals(siteCode))
                .filter(device -> status == null || device.status() == status)
                .sorted(Comparator.comparing(BmsDevice::siteCode).thenComparing(BmsDevice::deviceCode))
                .toList();
    }

    // ---- channel states --------------------------------------------------------------------------

    @Override
    public Optional<ChannelState> findChannelState(UUID deviceId, String channel) {
        return channelStates.values().stream()
                .filter(state -> state.deviceId().equals(deviceId) && state.channel().equals(channel))
                .findFirst();
    }

    @Override
    public List<ChannelState> findChannelStates(Collection<UUID> deviceIds) {
        return channelStates.values().stream().filter(state -> deviceIds.contains(state.deviceId())).toList();
    }

    @Override
    public List<ChannelState> findPendingBreaches(int limit) {
        return channelStates.values().stream()
                .filter(state -> state.breachRuleId() != null && state.alertId() == null)
                .sorted(Comparator.comparing(ChannelState::breachStartedAt))
                .limit(limit)
                .toList();
    }

    @Override
    public List<ChannelState> findBuildingChannels(String siteCode, String buildingCode, MeasuredQuantity quantity) {
        return channelStates.values().stream()
                .filter(state -> siteCode.equals(state.siteCode()) && buildingCode.equals(state.buildingCode())
                        && state.quantity() == quantity)
                .toList();
    }

    @Override
    public ChannelState saveChannelState(ChannelState state) {
        channelStates.put(state.id(), state);
        return state;
    }

    // ---- readings --------------------------------------------------------------------------------

    @Override
    public TelemetryReading saveReading(TelemetryReading reading) {
        readings.put(reading.id(), reading);
        return reading;
    }

    @Override
    public List<TelemetryReading> findReadings(ReadingQuery query) {
        return readings.values().stream()
                .filter(reading -> reading.siteCode().equals(query.siteCode()))
                .filter(reading -> query.deviceId() == null || reading.deviceId().equals(query.deviceId()))
                .filter(reading -> query.channel() == null || reading.channel().equals(query.channel()))
                .filter(reading -> query.from() == null || !reading.observedAt().isBefore(query.from()))
                .filter(reading -> query.to() == null || reading.observedAt().isBefore(query.to()))
                .sorted(Comparator.comparing(TelemetryReading::observedAt).reversed())
                .limit(query.limit())
                .toList();
    }

    @Override
    public List<TelemetryReading> findReadingsByMessage(String sourceId, String idempotencyKey) {
        return readings.values().stream()
                .filter(reading -> java.util.Objects.equals(reading.sourceId(), sourceId)
                        && java.util.Objects.equals(reading.idempotencyKey(), idempotencyKey))
                .sorted(Comparator.comparingInt(TelemetryReading::itemIndex))
                .toList();
    }

    @Override
    public void holdAsEvidence(Collection<UUID> readingIds) {
        for (UUID id : readingIds) {
            TelemetryReading reading = readings.get(id);
            if (reading != null && !reading.evidenceHold()) {
                readings.put(id, new TelemetryReading(reading.id(), reading.siteCode(), reading.deviceId(),
                        reading.avampAssetId(), reading.deviceCode(), reading.buildingCode(), reading.roomId(),
                        reading.locationCode(), reading.channel(), reading.quantity(), reading.value(),
                        reading.observedAt(), reading.receivedAt(), reading.sourceId(), reading.format(),
                        reading.idempotencyKey(), reading.itemIndex(), reading.inboxId(),
                        reading.releasedFromQuarantineId(), true, reading.metadata()));
            }
        }
    }

    @Override
    public int purgeReadings(String siteCode, Instant cutoff) {
        List<UUID> toRemove = readings.values().stream()
                .filter(reading -> reading.siteCode().equals(siteCode) && reading.observedAt().isBefore(cutoff)
                        && !reading.evidenceHold())
                .map(TelemetryReading::id)
                .toList();
        toRemove.forEach(readings::remove);
        return toRemove.size();
    }

    // ---- quarantine ------------------------------------------------------------------------------

    @Override
    public QuarantinedReading saveQuarantine(QuarantinedReading reading) {
        quarantine.put(reading.id(), reading);
        return reading;
    }

    @Override
    public Optional<QuarantinedReading> findQuarantine(UUID id) {
        return Optional.ofNullable(quarantine.get(id));
    }

    @Override
    public List<QuarantinedReading> findQuarantine(String siteCode, QuarantineStatus status) {
        return quarantine.values().stream()
                .filter(reading -> siteCode == null || reading.siteCode().equals(siteCode))
                .filter(reading -> status == null || reading.status() == status)
                .toList();
    }

    @Override
    public List<QuarantinedReading> findQuarantineByMessage(String sourceId, String idempotencyKey) {
        return quarantine.values().stream()
                .filter(reading -> java.util.Objects.equals(reading.sourceId(), sourceId)
                        && java.util.Objects.equals(reading.idempotencyKey(), idempotencyKey))
                .sorted(Comparator.comparingInt(QuarantinedReading::itemIndex))
                .toList();
    }

    // ---- rules -----------------------------------------------------------------------------------

    @Override
    public ThresholdRule saveRule(ThresholdRule rule) {
        rules.put(rule.id(), rule);
        return rule;
    }

    @Override
    public Optional<ThresholdRule> findCurrentRule(UUID ruleId) {
        return rules.values().stream().filter(rule -> rule.ruleId().equals(ruleId) && rule.isCurrent()).findFirst();
    }

    @Override
    public List<ThresholdRule> findRuleVersions(UUID ruleId) {
        return rules.values().stream().filter(rule -> rule.ruleId().equals(ruleId))
                .sorted(Comparator.comparingInt(ThresholdRule::ruleVersion)).toList();
    }

    @Override
    public List<ThresholdRule> findCurrentRules(String siteCode) {
        return rules.values().stream().filter(rule -> rule.siteCode().equals(siteCode) && rule.isCurrent()).toList();
    }

    @Override
    public RuleConflict saveConflict(RuleConflict conflict) {
        conflicts.put(conflict.id(), conflict);
        return conflict;
    }

    @Override
    public boolean conflictLogged(UUID ruleId, UUID conflictingRuleId, UUID winningRuleId) {
        return conflicts.values().stream().anyMatch(conflict -> conflict.ruleId().equals(ruleId)
                && conflict.conflictingRuleId().equals(conflictingRuleId)
                && conflict.winningRuleId().equals(winningRuleId));
    }

    @Override
    public List<RuleConflict> findConflicts(String siteCode) {
        return conflicts.values().stream().filter(conflict -> conflict.siteCode().equals(siteCode)).toList();
    }

    // ---- alerts ----------------------------------------------------------------------------------

    @Override
    public BmsAlert saveAlert(BmsAlert alert) {
        alerts.put(alert.id(), alert);
        return alert;
    }

    @Override
    public Optional<BmsAlert> findAlert(UUID id) {
        return Optional.ofNullable(alerts.get(id));
    }

    @Override
    public List<BmsAlert> findAlerts(String siteCode, AlertStatus status) {
        return alerts.values().stream()
                .filter(alert -> siteCode == null || alert.siteCode().equals(siteCode))
                .filter(alert -> status == null || alert.status() == status)
                .toList();
    }

    @Override
    public List<BmsAlert> findAlertsWithWorkOrder(UUID deviceId) {
        return alerts.values().stream()
                .filter(alert -> alert.deviceId().equals(deviceId) && alert.hasWorkOrder())
                .sorted(Comparator.comparing(BmsAlert::raisedAt).reversed())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    @Override
    public Optional<BmsAlert> findActiveAlert(UUID deviceId, AlertType type) {
        return alerts.values().stream()
                .filter(alert -> alert.deviceId().equals(deviceId) && alert.type() == type && alert.isActive())
                .findFirst();
    }

    @Override
    public List<BmsAlert> findActiveAlerts(Collection<UUID> deviceIds) {
        return alerts.values().stream()
                .filter(alert -> deviceIds.contains(alert.deviceId()) && alert.isActive())
                .sorted(Comparator.comparing(BmsAlert::raisedAt).reversed())
                .toList();
    }
}
