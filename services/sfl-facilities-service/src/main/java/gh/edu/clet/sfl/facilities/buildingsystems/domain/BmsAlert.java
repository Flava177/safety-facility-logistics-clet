package gh.edu.clet.sfl.facilities.buildingsystems.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * An automated alert - SRS-SFL-S156-02, -03.
 *
 * <p>Raised only for a condition that is real: a breach that outlasted its debounce, a critical fault, or
 * a device silent for longer than the offline window. A transient breach never becomes one of these.
 *
 * <h2>Correlation, not duplication</h2>
 *
 * <p>"Repeated breaches on the same asset are correlated rather than raising duplicate work orders." When
 * a sustained breach is found on a device that already has an alert whose S153 work order is still open,
 * {@link #correlate} links it here - one more occurrence, more evidence - and no new work order is cut.
 * Only once that work order has closed does a new breach earn a new alert and a new work order.
 *
 * @param evidenceReadingIds the readings that justify it, carried into the work order as its evidence
 *        back-reference (S156-02 validation). Capped at {@link #MAX_EVIDENCE}
 * @param ruleVersion the version of the rule that raised it, so a later threshold change cannot rewrite
 *        why it was raised
 */
public record BmsAlert(
        UUID id,
        String siteCode,
        UUID deviceId,
        String avampAssetId,
        String deviceCode,
        String buildingCode,
        UUID roomId,
        String locationCode,
        BuildingSystemType systemType,
        AlertType type,
        CriticalFaultType criticalFault,
        UUID ruleId,
        Integer ruleVersion,
        AlertPriority priority,
        AlertStatus status,
        String summary,
        List<UUID> evidenceReadingIds,
        int occurrences,
        Instant raisedAt,
        Instant lastOccurredAt,
        Instant clearedAt,
        UUID workOrderId,
        String workOrderNumber,
        RecordMetadata metadata) {

    public static final int MAX_EVIDENCE = 50;

    public BmsAlert {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(siteCode, "siteCode is required");
        Objects.requireNonNull(deviceId, "deviceId is required");
        Objects.requireNonNull(type, "type is required");
        Objects.requireNonNull(priority, "priority is required");
        Objects.requireNonNull(status, "status is required");
        evidenceReadingIds = evidenceReadingIds == null ? List.of() : List.copyOf(evidenceReadingIds);
        Objects.requireNonNull(raisedAt, "raisedAt is required");
        Objects.requireNonNull(metadata, "metadata is required");
        if (type == AlertType.CRITICAL_FAULT && criticalFault == null) {
            throw new IllegalArgumentException("a critical-fault alert must name the critical fault");
        }
    }

    public static BmsAlert raise(UUID id, BmsDevice device, AlertType type, CriticalFaultType criticalFault,
            ThresholdRule rule, AlertPriority priority, String summary, List<UUID> evidence, String actorId,
            Instant at, SourceChannel channel, String correlationId) {
        return new BmsAlert(id, device.siteCode(), device.id(), device.avampAssetId(), device.deviceCode(),
                device.buildingCode(), device.roomId(), device.locationCode(), device.systemType(), type, criticalFault,
                rule == null ? null : rule.ruleId(), rule == null ? null : rule.ruleVersion(), priority,
                AlertStatus.ACTIVE, summary, capped(evidence), 1, at, at, null, null, null,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    public boolean isActive() {
        return status == AlertStatus.ACTIVE;
    }

    public boolean hasWorkOrder() {
        return workOrderId != null;
    }

    public BmsAlert linkWorkOrder(UUID orderId, String orderNumber) {
        if (hasWorkOrder()) {
            throw new FacilitiesException.InvalidStateTransitionException("Alert " + id + " already has a work order");
        }
        return new BmsAlert(id, siteCode, deviceId, avampAssetId, deviceCode, buildingCode, roomId, locationCode,
                systemType, type, criticalFault, ruleId, ruleVersion, priority, status, summary, evidenceReadingIds,
                occurrences, raisedAt, lastOccurredAt, clearedAt, orderId, orderNumber, metadata);
    }

    /** A further sustained breach on the same asset, linked here rather than duplicated. */
    public BmsAlert correlate(List<UUID> evidence, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        List<UUID> merged = new ArrayList<>(evidenceReadingIds);
        merged.addAll(evidence);
        return new BmsAlert(id, siteCode, deviceId, avampAssetId, deviceCode, buildingCode, roomId, locationCode,
                systemType, type, criticalFault, ruleId, ruleVersion, priority, AlertStatus.ACTIVE, summary,
                capped(merged), occurrences + 1, raisedAt, at, null, workOrderId, workOrderNumber,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public BmsAlert clear(String actorId, Instant at, SourceChannel channel, String correlationId) {
        if (!isActive()) {
            return this;
        }
        return new BmsAlert(id, siteCode, deviceId, avampAssetId, deviceCode, buildingCode, roomId, locationCode,
                systemType, type, criticalFault, ruleId, ruleVersion, priority, AlertStatus.CLEARED, summary,
                evidenceReadingIds, occurrences, raisedAt, lastOccurredAt, at, workOrderId, workOrderNumber,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    private static List<UUID> capped(List<UUID> evidence) {
        List<UUID> distinct = new ArrayList<>(new LinkedHashSet<>(evidence == null ? List.of() : evidence));
        return distinct.size() <= MAX_EVIDENCE ? distinct : distinct.subList(distinct.size() - MAX_EVIDENCE,
                distinct.size());
    }
}
