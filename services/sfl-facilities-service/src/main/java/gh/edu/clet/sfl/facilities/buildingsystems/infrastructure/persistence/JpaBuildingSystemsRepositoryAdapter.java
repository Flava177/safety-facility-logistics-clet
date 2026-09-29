package gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.persistence;

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
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/**
 * The one adapter behind {@link BuildingSystemsRepository}, over the eight V16 tables.
 *
 * <p>A {@code null} {@code siteCode} on a list method means "every site the RLS session can see" - the
 * caller has already narrowed by permission before reaching here; this class only translates between the
 * domain records and their JPA mappings.
 */
@Repository
class JpaBuildingSystemsRepositoryAdapter implements BuildingSystemsRepository {

    private final JpaAvampAssetProjectionJpaRepository avampAssets;
    private final JpaBmsDeviceJpaRepository devices;
    private final JpaChannelStateJpaRepository channelStates;
    private final JpaTelemetryReadingJpaRepository readings;
    private final JpaQuarantinedReadingJpaRepository quarantine;
    private final JpaThresholdRuleJpaRepository rules;
    private final JpaRuleConflictJpaRepository conflicts;
    private final JpaBmsAlertJpaRepository alerts;

    JpaBuildingSystemsRepositoryAdapter(JpaAvampAssetProjectionJpaRepository avampAssets,
            JpaBmsDeviceJpaRepository devices, JpaChannelStateJpaRepository channelStates,
            JpaTelemetryReadingJpaRepository readings, JpaQuarantinedReadingJpaRepository quarantine,
            JpaThresholdRuleJpaRepository rules, JpaRuleConflictJpaRepository conflicts,
            JpaBmsAlertJpaRepository alerts) {
        this.avampAssets = avampAssets;
        this.devices = devices;
        this.channelStates = channelStates;
        this.readings = readings;
        this.quarantine = quarantine;
        this.rules = rules;
        this.conflicts = conflicts;
        this.alerts = alerts;
    }

    // ---- AVAMP projection ------------------------------------------------------------------------

    @Override
    public Optional<AvampAssetProjection> findAvampAsset(String avampAssetId) {
        return avampAssets.findByAvampAssetId(avampAssetId).map(AvampAssetProjectionRecord::toDomain);
    }

    @Override
    public AvampAssetProjection saveAvampAsset(AvampAssetProjection asset) {
        AvampAssetProjectionRecord record = avampAssets.findById(asset.id()).orElseGet(AvampAssetProjectionRecord::empty);
        record.apply(asset);
        return avampAssets.save(record).toDomain();
    }

    // ---- devices ---------------------------------------------------------------------------------

    @Override
    public BmsDevice saveDevice(BmsDevice device) {
        BmsDeviceRecord record = devices.findById(device.id()).orElseGet(BmsDeviceRecord::empty);
        record.apply(device);
        return devices.save(record).toDomain();
    }

    @Override
    public Optional<BmsDevice> findDevice(UUID id) {
        return devices.findById(id).map(BmsDeviceRecord::toDomain);
    }

    @Override
    public Optional<BmsDevice> findActiveDeviceByCode(String siteCode, String deviceCode) {
        return devices.findBySiteCodeAndDeviceCodeAndStatus(siteCode, deviceCode, DeviceStatus.ACTIVE)
                .map(BmsDeviceRecord::toDomain);
    }

    @Override
    public Optional<BmsDevice> findDeviceByAvampAssetId(String avampAssetId) {
        return devices.findByAvampAssetId(avampAssetId).map(BmsDeviceRecord::toDomain);
    }

    @Override
    public List<BmsDevice> findDevices(String siteCode, DeviceStatus status) {
        List<BmsDeviceRecord> found;
        if (siteCode != null && status != null) {
            found = devices.findBySiteCodeAndStatus(siteCode, status);
        } else if (siteCode != null) {
            found = devices.findBySiteCode(siteCode);
        } else if (status != null) {
            found = devices.findByStatus(status);
        } else {
            found = devices.findAllByOrderBySiteCode();
        }
        return found.stream().map(BmsDeviceRecord::toDomain).toList();
    }

    // ---- channel states --------------------------------------------------------------------------

    @Override
    public Optional<ChannelState> findChannelState(UUID deviceId, String channel) {
        return channelStates.findByDeviceIdAndChannel(deviceId, channel).map(ChannelStateRecord::toDomain);
    }

    @Override
    public List<ChannelState> findChannelStates(Collection<UUID> deviceIds) {
        if (deviceIds.isEmpty()) {
            return List.of();
        }
        return channelStates.findByDeviceIdIn(deviceIds).stream().map(ChannelStateRecord::toDomain).toList();
    }

    @Override
    public List<ChannelState> findPendingBreaches(int limit) {
        return channelStates.findPendingBreaches(PageRequest.of(0, Math.max(1, limit))).stream()
                .map(ChannelStateRecord::toDomain).toList();
    }

    @Override
    public List<ChannelState> findBuildingChannels(String siteCode, String buildingCode, MeasuredQuantity quantity) {
        return channelStates.findBySiteCodeAndBuildingCodeAndQuantity(siteCode, buildingCode, quantity).stream()
                .map(ChannelStateRecord::toDomain).toList();
    }

    @Override
    public ChannelState saveChannelState(ChannelState state) {
        ChannelStateRecord record = channelStates.findById(state.id()).orElseGet(ChannelStateRecord::empty);
        record.apply(state);
        return channelStates.save(record).toDomain();
    }

    // ---- readings --------------------------------------------------------------------------------

    @Override
    public TelemetryReading saveReading(TelemetryReading reading) {
        return readings.save(TelemetryReadingRecord.of(reading)).toDomain();
    }

    @Override
    public List<TelemetryReading> findReadings(ReadingQuery query) {
        return readings.search(query.siteCode(), query.deviceId(), query.channel(), query.from(), query.to(),
                PageRequest.of(0, query.limit())).stream().map(TelemetryReadingRecord::toDomain).toList();
    }

    @Override
    public List<TelemetryReading> findReadingsByMessage(String sourceId, String idempotencyKey) {
        return readings.findBySourceIdAndIdempotencyKeyOrderByItemIndex(sourceId, idempotencyKey).stream()
                .map(TelemetryReadingRecord::toDomain).toList();
    }

    @Override
    public void holdAsEvidence(Collection<UUID> readingIds) {
        if (!readingIds.isEmpty()) {
            readings.holdAsEvidence(List.copyOf(readingIds));
        }
    }

    @Override
    public int purgeReadings(String siteCode, java.time.Instant cutoff) {
        return readings.purge(siteCode, cutoff);
    }

    // ---- quarantine ------------------------------------------------------------------------------

    @Override
    public QuarantinedReading saveQuarantine(QuarantinedReading reading) {
        QuarantinedReadingRecord record = quarantine.findById(reading.id()).orElseGet(QuarantinedReadingRecord::empty);
        record.apply(reading);
        return quarantine.save(record).toDomain();
    }

    @Override
    public Optional<QuarantinedReading> findQuarantine(UUID id) {
        return quarantine.findById(id).map(QuarantinedReadingRecord::toDomain);
    }

    @Override
    public List<QuarantinedReading> findQuarantine(String siteCode, QuarantineStatus status) {
        List<QuarantinedReadingRecord> found;
        if (siteCode != null && status != null) {
            found = quarantine.findBySiteCodeAndStatus(siteCode, status);
        } else if (siteCode != null) {
            found = quarantine.findBySiteCode(siteCode);
        } else if (status != null) {
            found = quarantine.findByStatus(status);
        } else {
            found = quarantine.findAllByOrderBySiteCode();
        }
        return found.stream().map(QuarantinedReadingRecord::toDomain).toList();
    }

    @Override
    public List<QuarantinedReading> findQuarantineByMessage(String sourceId, String idempotencyKey) {
        return quarantine.findBySourceIdAndIdempotencyKeyOrderByItemIndex(sourceId, idempotencyKey).stream()
                .map(QuarantinedReadingRecord::toDomain).toList();
    }

    // ---- rules -----------------------------------------------------------------------------------

    @Override
    public ThresholdRule saveRule(ThresholdRule rule) {
        ThresholdRuleRecord record = rules.findById(rule.id()).orElseGet(ThresholdRuleRecord::empty);
        record.apply(rule);
        return rules.save(record).toDomain();
    }

    @Override
    public Optional<ThresholdRule> findCurrentRule(UUID ruleId) {
        return rules.findByRuleIdAndSupersededAtIsNull(ruleId).map(ThresholdRuleRecord::toDomain);
    }

    @Override
    public List<ThresholdRule> findRuleVersions(UUID ruleId) {
        return rules.findByRuleIdOrderByRuleVersion(ruleId).stream().map(ThresholdRuleRecord::toDomain).toList();
    }

    @Override
    public List<ThresholdRule> findCurrentRules(String siteCode) {
        return rules.findBySiteCodeAndSupersededAtIsNull(siteCode).stream().map(ThresholdRuleRecord::toDomain)
                .toList();
    }

    @Override
    public RuleConflict saveConflict(RuleConflict conflict) {
        return conflicts.save(RuleConflictRecord.of(conflict)).toDomain();
    }

    @Override
    public boolean conflictLogged(UUID ruleId, UUID conflictingRuleId, UUID winningRuleId) {
        return conflicts.existsByRuleIdAndConflictingRuleIdAndWinningRuleId(ruleId, conflictingRuleId, winningRuleId);
    }

    @Override
    public List<RuleConflict> findConflicts(String siteCode) {
        return conflicts.findBySiteCode(siteCode).stream().map(RuleConflictRecord::toDomain).toList();
    }

    // ---- alerts ----------------------------------------------------------------------------------

    @Override
    public BmsAlert saveAlert(BmsAlert alert) {
        BmsAlertRecord record = alerts.findById(alert.id()).orElseGet(BmsAlertRecord::empty);
        record.apply(alert);
        return alerts.save(record).toDomain();
    }

    @Override
    public Optional<BmsAlert> findAlert(UUID id) {
        return alerts.findById(id).map(BmsAlertRecord::toDomain);
    }

    @Override
    public List<BmsAlert> findAlerts(String siteCode, AlertStatus status) {
        List<BmsAlertRecord> found;
        if (siteCode != null && status != null) {
            found = alerts.findBySiteCodeAndStatus(siteCode, status);
        } else if (siteCode != null) {
            found = alerts.findBySiteCode(siteCode);
        } else if (status != null) {
            found = alerts.findByStatus(status);
        } else {
            found = alerts.findAllByOrderBySiteCode();
        }
        return found.stream().map(BmsAlertRecord::toDomain).toList();
    }

    @Override
    public List<BmsAlert> findAlertsWithWorkOrder(UUID deviceId) {
        return alerts.findByDeviceIdAndWorkOrderIdIsNotNullOrderByRaisedAtDesc(deviceId).stream()
                .map(BmsAlertRecord::toDomain).toList();
    }

    @Override
    public Optional<BmsAlert> findActiveAlert(UUID deviceId, AlertType type) {
        return alerts.findByDeviceIdAndTypeAndStatus(deviceId, type, AlertStatus.ACTIVE).map(BmsAlertRecord::toDomain);
    }

    @Override
    public List<BmsAlert> findActiveAlerts(Collection<UUID> deviceIds) {
        if (deviceIds.isEmpty()) {
            return List.of();
        }
        return alerts.findByDeviceIdInAndStatus(deviceIds, AlertStatus.ACTIVE).stream()
                .map(BmsAlertRecord::toDomain)
                .sorted(Comparator.comparing(BmsAlert::raisedAt).reversed())
                .toList();
    }
}
