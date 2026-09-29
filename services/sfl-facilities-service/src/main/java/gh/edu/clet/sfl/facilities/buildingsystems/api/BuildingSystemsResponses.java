package gh.edu.clet.sfl.facilities.buildingsystems.api;

import gh.edu.clet.sfl.facilities.buildingsystems.application.BuildingHealthService;
import gh.edu.clet.sfl.facilities.buildingsystems.application.BuildingSystemsCommands.IngestionResult;
import gh.edu.clet.sfl.facilities.buildingsystems.application.ThresholdRuleService.RuleChange;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.AlertPriority;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.AlertStatus;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.AlertType;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BmsAlert;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BmsDevice;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BuildingSystemType;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.CriticalFaultType;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.DeviceKind;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.DeviceStatus;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.MeasuredQuantity;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.QuarantineReason;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.QuarantineStatus;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.QuarantinedReading;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.RuleCondition;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.RuleConflict;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.TelemetryReading;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.ThresholdRule;
import gh.edu.clet.sfl.facilities.shared.application.vendor.VendorIntegrationRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The S156 response shapes. */
public final class BuildingSystemsResponses {

    private BuildingSystemsResponses() {
    }

    public record DeviceResponse(UUID id, String siteCode, String deviceCode, String avampAssetId, String name,
            BuildingSystemType systemType, DeviceKind kind, String buildingCode, UUID roomId, String roomCode,
            int expectedIntervalSeconds, LocalDate installedOn, String firmwareVersion, LocalDate firmwareReviewDueOn,
            LocalDate warrantyExpiresOn, LocalDate calibrationDueOn, DeviceStatus status, Instant retiredAt,
            String retiredBy, String retirementReason, long version) {

        public static DeviceResponse from(BmsDevice device) {
            return new DeviceResponse(device.id(), device.siteCode(), device.deviceCode(), device.avampAssetId(),
                    device.name(), device.systemType(), device.kind(), device.buildingCode(), device.roomId(),
                    device.roomCode(), device.expectedIntervalSeconds(), device.installedOn(), device.firmwareVersion(),
                    device.firmwareReviewDueOn(), device.warrantyExpiresOn(), device.calibrationDueOn(),
                    device.status(), device.retiredAt(), device.retiredBy(), device.retirementReason(),
                    device.metadata().version());
        }
    }

    public record RuleResponse(UUID versionId, UUID ruleId, int ruleVersion, String siteCode, String name,
            MeasuredQuantity quantity, BuildingSystemType systemType, UUID deviceId, String buildingCode, UUID roomId,
            RuleCondition condition, BigDecimal lowerLimit, BigDecimal upperLimit, Set<Integer> codes,
            Duration debounce, AlertPriority priority, boolean enabled, String changeReason, String overrideReason,
            String accountableOwner) {

        public static RuleResponse from(ThresholdRule rule) {
            return new RuleResponse(rule.id(), rule.ruleId(), rule.ruleVersion(), rule.siteCode(), rule.name(),
                    rule.quantity(), rule.systemType(), rule.deviceId(), rule.buildingCode(), rule.roomId(),
                    rule.condition(), rule.lowerLimit(), rule.upperLimit(), rule.codes(), rule.debounce(),
                    rule.priority(), rule.enabled(), rule.changeReason(), rule.overrideReason(),
                    rule.accountableOwner());
        }
    }

    public record ConflictResponse(UUID id, String siteCode, UUID ruleId, UUID conflictingRuleId, UUID winningRuleId,
            MeasuredQuantity quantity, String detail, Instant detectedAt) {

        public static ConflictResponse from(RuleConflict conflict) {
            return new ConflictResponse(conflict.id(), conflict.siteCode(), conflict.ruleId(),
                    conflict.conflictingRuleId(), conflict.winningRuleId(), conflict.quantity(), conflict.detail(),
                    conflict.detectedAt());
        }
    }

    public record RuleChangeResponse(RuleResponse rule, List<ConflictResponse> conflicts) {

        public static RuleChangeResponse from(RuleChange change) {
            return new RuleChangeResponse(RuleResponse.from(change.rule()),
                    change.conflicts().stream().map(ConflictResponse::from).toList());
        }
    }

    public record AlertResponse(UUID id, String siteCode, UUID deviceId, String avampAssetId, String deviceCode,
            String buildingCode, UUID roomId, String locationCode, BuildingSystemType systemType, AlertType type,
            CriticalFaultType criticalFault, UUID ruleId, Integer ruleVersion, AlertPriority priority,
            AlertStatus status, String summary, List<UUID> evidenceReadingIds, int occurrences, Instant raisedAt,
            Instant lastOccurredAt, Instant clearedAt, UUID workOrderId, String workOrderNumber) {

        public static AlertResponse from(BmsAlert alert) {
            return new AlertResponse(alert.id(), alert.siteCode(), alert.deviceId(), alert.avampAssetId(),
                    alert.deviceCode(), alert.buildingCode(), alert.roomId(), alert.locationCode(), alert.systemType(),
                    alert.type(), alert.criticalFault(), alert.ruleId(), alert.ruleVersion(), alert.priority(),
                    alert.status(), alert.summary(), alert.evidenceReadingIds(), alert.occurrences(), alert.raisedAt(),
                    alert.lastOccurredAt(), alert.clearedAt(), alert.workOrderId(), alert.workOrderNumber());
        }
    }

    public record ReadingResponse(UUID id, String siteCode, UUID deviceId, String deviceCode, String locationCode,
            String channel, MeasuredQuantity quantity, BigDecimal value, Instant observedAt, Instant receivedAt) {

        public static ReadingResponse from(TelemetryReading reading) {
            return new ReadingResponse(reading.id(), reading.siteCode(), reading.deviceId(), reading.deviceCode(),
                    reading.locationCode(), reading.channel(), reading.quantity(), reading.value(),
                    reading.observedAt(), reading.receivedAt());
        }
    }

    public record QuarantineResponse(UUID id, String siteCode, String sourceId, String deviceCode, String channel,
            MeasuredQuantity quantity, BigDecimal value, Instant observedAt, QuarantineReason reason, String detail,
            UUID deviceId, QuarantineStatus status, String resolvedBy, Instant resolvedAt, String resolutionNote,
            UUID releasedReadingId) {

        public static QuarantineResponse from(QuarantinedReading reading) {
            return new QuarantineResponse(reading.id(), reading.siteCode(), reading.sourceId(), reading.deviceCode(),
                    reading.channel(), reading.quantity(), reading.value(), reading.observedAt(), reading.reason(),
                    reading.detail(), reading.deviceId(), reading.status(), reading.resolvedBy(), reading.resolvedAt(),
                    reading.resolutionNote(), reading.releasedReadingId());
        }
    }

    public record IngestionItemResponse(int index, String deviceCode, String channel, String outcome,
            UUID readingId, UUID quarantineId, String code) {
    }

    public record IngestionResponse(UUID inboxId, String siteCode, boolean duplicate,
            List<IngestionItemResponse> items) {

        public static IngestionResponse from(IngestionResult result) {
            return new IngestionResponse(result.inboxId(), result.siteCode(), result.duplicate(),
                    result.items().stream()
                            .map(item -> new IngestionItemResponse(item.index(), item.deviceCode(), item.channel(),
                                    item.outcome().name(), item.readingId(), item.quarantineId(), item.code()))
                            .toList());
        }
    }

    public record DeviceHealthResponse(UUID deviceId, String deviceCode, String avampAssetId, String locationCode,
            String state, String reason, Instant lastObservedAt, List<UUID> activeAlertIds,
            List<String> workOrderNumbers) {

        static DeviceHealthResponse from(BuildingHealthService.DeviceHealthView view) {
            return new DeviceHealthResponse(view.deviceId(), view.deviceCode(), view.avampAssetId(),
                    view.locationCode(), view.state().name(), view.reason(), view.lastObservedAt(),
                    view.activeAlertIds(), view.workOrderNumbers());
        }
    }

    public record SystemHealthResponse(BuildingSystemType systemType, String state, List<DeviceHealthResponse> devices) {

        static SystemHealthResponse from(BuildingHealthService.SystemHealth system) {
            return new SystemHealthResponse(system.systemType(), system.state().name(),
                    system.devices().stream().map(DeviceHealthResponse::from).toList());
        }
    }

    public record BuildingHealthResponse(String buildingCode, String state, List<SystemHealthResponse> systems) {

        static BuildingHealthResponse from(BuildingHealthService.BuildingHealth building) {
            return new BuildingHealthResponse(building.buildingCode(), building.state().name(),
                    building.systems().stream().map(SystemHealthResponse::from).toList());
        }
    }

    public record SiteHealthResponse(String siteCode, String state, Instant generatedAt, int activeAlerts,
            int linkedWorkOrders, VendorIntegrationRegistry.GateStatus procurementGate,
            List<BuildingHealthResponse> buildings) {

        public static SiteHealthResponse from(BuildingHealthService.SiteHealth health) {
            return new SiteHealthResponse(health.siteCode(), health.state().name(), health.generatedAt(),
                    health.activeAlerts(), health.linkedWorkOrders(), health.procurementGate(),
                    health.buildings().stream().map(BuildingHealthResponse::from).toList());
        }
    }
}
