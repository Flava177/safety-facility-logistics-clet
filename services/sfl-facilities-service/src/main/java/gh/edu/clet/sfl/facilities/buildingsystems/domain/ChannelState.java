package gh.edu.clet.sfl.facilities.buildingsystems.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The live state of one device channel - its watermark, its latest value and any breach in progress.
 *
 * <p>Three S156 rules need a per-channel memory and each reads it here rather than scanning history:
 *
 * <ul>
 *   <li><strong>Monotonic timestamps</strong> (S156-01 validation) compare against {@code lastObservedAt}.</li>
 *   <li><strong>Debounce</strong> (S156-02) needs to know when the current breach started and which
 *       readings evidence it. A breach that clears before its window leaves nothing behind but this
 *       row's reset fields - "transient noise" raises no alert, no event and no work order, by
 *       construction rather than by a check.</li>
 *   <li><strong>Staleness and offline</strong> (S156-03) read {@code lastObservedAt} and
 *       {@code lastReceivedAt}.</li>
 * </ul>
 *
 * <p>Operational state, not a governed record: it changes on every reading and is not audited per change
 * - the reading, the alert and the work order it leads to are. {@code systemType} and
 * {@code buildingCode} are copied from the device so "is there a power outage in this building" is one
 * indexed read on the critical-fault path.
 */
public record ChannelState(
        UUID id,
        String siteCode,
        UUID deviceId,
        String channel,
        MeasuredQuantity quantity,
        BuildingSystemType systemType,
        String buildingCode,
        Instant lastObservedAt,
        Instant lastReceivedAt,
        BigDecimal lastValue,
        UUID lastReadingId,
        UUID breachRuleId,
        Instant breachStartedAt,
        List<UUID> breachReadingIds,
        UUID alertId,
        UUID criticalAlertId,
        RecordMetadata metadata) {

    /** Evidence ids kept per breach. Enough for a technician; bounded so a week-long breach is not a megabyte. */
    public static final int MAX_EVIDENCE = 20;

    public ChannelState {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(deviceId, "deviceId is required");
        Objects.requireNonNull(channel, "channel is required");
        Objects.requireNonNull(quantity, "quantity is required");
        breachReadingIds = breachReadingIds == null ? List.of() : List.copyOf(breachReadingIds);
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static ChannelState open(UUID id, BmsDevice device, String channel, MeasuredQuantity quantity,
            String actorId, Instant at, SourceChannel sourceChannel, String correlationId) {
        return new ChannelState(id, device.siteCode(), device.id(), channel, quantity, device.systemType(),
                device.buildingCode(), null, null, null, null, null, null, List.of(), null, null,
                RecordMetadata.createdBy(actorId, at, sourceChannel, correlationId));
    }

    public boolean inBreach() {
        return breachRuleId != null;
    }

    /**
     * Takes a reading into the watermark.
     *
     * <p>A reading slightly older than the watermark (within the clock-skew tolerance) is accepted as fact
     * but does not move the watermark or the "latest value" backwards: the channel's current state is what
     * the newest reading said, not the most recently delivered one.
     */
    public ChannelState observe(UUID readingId, BigDecimal value, Instant observedAt, Instant receivedAt,
            String actorId, SourceChannel sourceChannel, String correlationId) {
        boolean newest = lastObservedAt == null || !observedAt.isBefore(lastObservedAt);
        return new ChannelState(id, siteCode, deviceId, channel, quantity, systemType, buildingCode,
                newest ? observedAt : lastObservedAt, receivedAt, newest ? value : lastValue,
                newest ? readingId : lastReadingId, breachRuleId, breachStartedAt, breachReadingIds, alertId,
                criticalAlertId, metadata.modifiedBy(actorId, receivedAt, sourceChannel, correlationId));
    }

    /** The first breaching reading starts the debounce clock; later ones add evidence to it. */
    public ChannelState breach(UUID ruleId, UUID readingId, Instant observedAt) {
        List<UUID> evidence = new ArrayList<>(inBreach() ? breachReadingIds : List.of());
        if (evidence.size() < MAX_EVIDENCE) {
            evidence.add(readingId);
        }
        return new ChannelState(id, siteCode, deviceId, channel, quantity, systemType, buildingCode, lastObservedAt,
                lastReceivedAt, lastValue, lastReadingId, ruleId, inBreach() ? breachStartedAt : observedAt, evidence,
                alertId, criticalAlertId, metadata);
    }

    /** Back in range: the breach, whether it was ever raised or not, is over. */
    public ChannelState breachCleared() {
        return new ChannelState(id, siteCode, deviceId, channel, quantity, systemType, buildingCode, lastObservedAt,
                lastReceivedAt, lastValue, lastReadingId, null, null, List.of(), null, criticalAlertId, metadata);
    }

    public ChannelState withAlert(UUID raisedAlertId) {
        return new ChannelState(id, siteCode, deviceId, channel, quantity, systemType, buildingCode, lastObservedAt,
                lastReceivedAt, lastValue, lastReadingId, breachRuleId, breachStartedAt, breachReadingIds,
                raisedAlertId, criticalAlertId, metadata);
    }

    public ChannelState withCriticalAlert(UUID raisedAlertId) {
        return new ChannelState(id, siteCode, deviceId, channel, quantity, systemType, buildingCode, lastObservedAt,
                lastReceivedAt, lastValue, lastReadingId, breachRuleId, breachStartedAt, breachReadingIds, alertId,
                raisedAlertId, metadata);
    }
}
