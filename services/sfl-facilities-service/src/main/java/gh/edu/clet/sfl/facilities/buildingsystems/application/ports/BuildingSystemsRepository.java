package gh.edu.clet.sfl.facilities.buildingsystems.application.ports;

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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for S156 - the V16 tables. One port for the module, as {@code BookingRepository} is for S159.
 *
 * <p>Every list method taking a {@code siteCode} treats {@code null} as "every site the database will show
 * this session" - the SQL filter is the first line, row-level security the second, and the service's
 * {@code filterBySite} the third. Nothing here deletes a device, a rule, an alert or a quarantined reading;
 * the only delete is the retention purge of readings, and it spares evidence.
 */
public interface BuildingSystemsRepository {

    // ---- AVAMP projection ------------------------------------------------------------------------

    Optional<AvampAssetProjection> findAvampAsset(String avampAssetId);

    AvampAssetProjection saveAvampAsset(AvampAssetProjection asset);

    // ---- devices ---------------------------------------------------------------------------------

    BmsDevice saveDevice(BmsDevice device);

    Optional<BmsDevice> findDevice(UUID id);

    Optional<BmsDevice> findActiveDeviceByCode(String siteCode, String deviceCode);

    Optional<BmsDevice> findDeviceByAvampAssetId(String avampAssetId);

    List<BmsDevice> findDevices(String siteCode, DeviceStatus status);

    // ---- channel states --------------------------------------------------------------------------

    Optional<ChannelState> findChannelState(UUID deviceId, String channel);

    List<ChannelState> findChannelStates(Collection<UUID> deviceIds);

    /** Channels with a breach under debounce and no alert yet - the sustained-breach sweep's input. */
    List<ChannelState> findPendingBreaches(int limit);

    List<ChannelState> findBuildingChannels(String siteCode, String buildingCode, MeasuredQuantity quantity);

    ChannelState saveChannelState(ChannelState state);

    // ---- readings --------------------------------------------------------------------------------

    TelemetryReading saveReading(TelemetryReading reading);

    List<TelemetryReading> findReadings(ReadingQuery query);

    List<TelemetryReading> findReadingsByMessage(String sourceId, String idempotencyKey);

    /** Marks readings as alert evidence, exempting them from the retention purge. */
    void holdAsEvidence(Collection<UUID> readingIds);

    /** Deletes readings observed before {@code cutoff} that no alert cites. Returns how many went. */
    int purgeReadings(String siteCode, Instant cutoff);

    // ---- quarantine ------------------------------------------------------------------------------

    QuarantinedReading saveQuarantine(QuarantinedReading reading);

    Optional<QuarantinedReading> findQuarantine(UUID id);

    List<QuarantinedReading> findQuarantine(String siteCode, QuarantineStatus status);

    List<QuarantinedReading> findQuarantineByMessage(String sourceId, String idempotencyKey);

    // ---- rules -----------------------------------------------------------------------------------

    /** Inserts a version row, or updates one (only ever to stamp {@code supersededAt}). */
    ThresholdRule saveRule(ThresholdRule rule);

    Optional<ThresholdRule> findCurrentRule(UUID ruleId);

    List<ThresholdRule> findRuleVersions(UUID ruleId);

    List<ThresholdRule> findCurrentRules(String siteCode);

    RuleConflict saveConflict(RuleConflict conflict);

    boolean conflictLogged(UUID ruleId, UUID conflictingRuleId, UUID winningRuleId);

    List<RuleConflict> findConflicts(String siteCode);

    // ---- alerts ----------------------------------------------------------------------------------

    BmsAlert saveAlert(BmsAlert alert);

    Optional<BmsAlert> findAlert(UUID id);

    List<BmsAlert> findAlerts(String siteCode, AlertStatus status);

    /** A device's alerts that carry a work order, newest first - the correlation check's input. */
    List<BmsAlert> findAlertsWithWorkOrder(UUID deviceId);

    Optional<BmsAlert> findActiveAlert(UUID deviceId, AlertType type);

    List<BmsAlert> findActiveAlerts(Collection<UUID> deviceIds);

    /**
     * @param from inclusive lower bound on observedAt, or null
     * @param to exclusive upper bound on observedAt, or null
     */
    record ReadingQuery(String siteCode, UUID deviceId, String channel, Instant from, Instant to, int limit) {

        public ReadingQuery {
            limit = limit <= 0 ? 200 : Math.min(limit, 1000);
        }
    }
}
