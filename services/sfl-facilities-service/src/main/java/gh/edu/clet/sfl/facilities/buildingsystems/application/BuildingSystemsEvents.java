package gh.edu.clet.sfl.facilities.buildingsystems.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BmsAlert;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Every event S156 publishes - documented in {@code docs/facilities/S156_Event_Contracts.md}.
 *
 * <p>One place for the names, so the contract document can be checked against one file, and one payload
 * builder for alerts, so the three alert events cannot drift into describing the same alert three ways.
 * Payloads carry references and classifications only (S217 rule): ids, codes, a priority, a reading id -
 * never a vendor payload or free text somebody typed.
 */
public final class BuildingSystemsEvents {

    public static final String READING_QUARANTINED = "sfl.ifimp.reading-quarantined.v1";
    public static final String ALERT_RAISED = "sfl.ifimp.bms-alert-raised.v1";
    public static final String ALERT_CORRELATED = "sfl.ifimp.bms-alert-correlated.v1";
    public static final String ALERT_CLEARED = "sfl.ifimp.bms-alert-cleared.v1";
    public static final String SENSOR_OFFLINE = "sfl.ifimp.bms-sensor-offline.v1";
    /**
     * The cross-service fast-lane signal for S162a / S174 in SSEMP (S156-03, NFR-PERF1). Recorded in the
     * outbox and drained to the broker; SSEMP has no consumer for it yet - see the gap report.
     */
    public static final String CRITICAL_FAULT_DETECTED = "sfl.ifimp.building-critical-fault-detected.v1";
    public static final String RULE_CHANGED = "sfl.ifimp.bms-rule-changed.v1";
    public static final String DEVICE_REGISTERED = "sfl.ifimp.bms-device-registered.v1";
    public static final String DEVICE_RETIRED = "sfl.ifimp.bms-device-retired.v1";
    public static final String DEVICE_LIFECYCLE_DUE = "sfl.ifimp.bms-device-lifecycle-due.v1";

    private BuildingSystemsEvents() {
    }

    static void publish(ServiceOutbox outbox, String eventType, String aggregateType, UUID aggregateId,
            String siteCode, ActorContext actor, Map<String, Object> payload) {
        outbox.record(eventType, 1, aggregateType, aggregateId, siteCode, actor.correlationId(), actor.actorId(),
                payload);
    }

    static Map<String, Object> alertPayload(BmsAlert alert) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("alertId", alert.id().toString());
        payload.put("siteCode", alert.siteCode());
        payload.put("buildingCode", alert.buildingCode());
        payload.put("roomId", alert.roomId() == null ? null : alert.roomId().toString());
        payload.put("locationCode", alert.locationCode());
        payload.put("deviceId", alert.deviceId().toString());
        payload.put("avampAssetId", alert.avampAssetId());
        payload.put("systemType", alert.systemType() == null ? null : alert.systemType().name());
        payload.put("alertType", alert.type().name());
        payload.put("criticalFault", alert.criticalFault() == null ? null : alert.criticalFault().name());
        payload.put("ruleId", alert.ruleId() == null ? null : alert.ruleId().toString());
        payload.put("ruleVersion", alert.ruleVersion());
        payload.put("priority", alert.priority().name());
        payload.put("status", alert.status().name());
        payload.put("occurrences", alert.occurrences());
        payload.put("evidenceReadingIds", alert.evidenceReadingIds().stream().map(UUID::toString).toList());
        payload.put("workOrderId", alert.workOrderId() == null ? null : alert.workOrderId().toString());
        payload.put("workOrderNumber", alert.workOrderNumber());
        payload.put("raisedAt", alert.raisedAt().toString());
        payload.put("lastOccurredAt", alert.lastOccurredAt() == null ? null : alert.lastOccurredAt().toString());
        payload.put("clearedAt", alert.clearedAt() == null ? null : alert.clearedAt().toString());
        return payload;
    }
}
