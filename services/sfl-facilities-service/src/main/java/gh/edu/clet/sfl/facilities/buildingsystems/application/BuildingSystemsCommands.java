package gh.edu.clet.sfl.facilities.buildingsystems.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.AlertPriority;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BuildingSystemType;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.DeviceKind;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.MeasuredQuantity;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.QuarantineReason;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.RuleCondition;
import gh.edu.clet.sfl.facilities.shared.application.vendor.SignedVendorMessage;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The S156 commands and the ingestion result. Every command carries its actor and channel. */
public final class BuildingSystemsCommands {

    private BuildingSystemsCommands() {
    }

    public record IngestTelemetry(SignedVendorMessage message, ActorContext actor, SourceChannel channel) {
    }

    public record RegisterDevice(String siteCode, String deviceCode, String avampAssetId, String name,
            BuildingSystemType systemType, DeviceKind kind, String buildingCode, UUID roomId,
            int expectedIntervalSeconds, LocalDate installedOn, String firmwareVersion, LocalDate firmwareReviewDueOn,
            LocalDate warrantyExpiresOn, LocalDate calibrationDueOn, ActorContext actor, SourceChannel channel) {
    }

    public record ReviseDevice(UUID deviceId, String name, String buildingCode, UUID roomId,
            int expectedIntervalSeconds, LocalDate installedOn, String firmwareVersion, LocalDate firmwareReviewDueOn,
            LocalDate warrantyExpiresOn, LocalDate calibrationDueOn, Long expectedVersion, ActorContext actor,
            SourceChannel channel) {
    }

    public record RetireDevice(UUID deviceId, String reason, Long expectedVersion, ActorContext actor,
            SourceChannel channel) {
    }

    /** @param debounce null takes the site's configured default */
    public record CreateRule(String siteCode, String name, MeasuredQuantity quantity, BuildingSystemType systemType,
            UUID deviceId, String buildingCode, UUID roomId, RuleCondition condition, BigDecimal lowerLimit,
            BigDecimal upperLimit, Set<Integer> codes, Duration debounce, AlertPriority priority, String reason,
            ActorContext actor, SourceChannel channel) {
    }

    /** @param expectedRuleVersion the version the author was looking at; a later version is a conflict */
    public record ReviseRule(UUID ruleId, String name, BuildingSystemType systemType, UUID deviceId,
            String buildingCode, UUID roomId, RuleCondition condition, BigDecimal lowerLimit, BigDecimal upperLimit,
            Set<Integer> codes, Duration debounce, AlertPriority priority, String reason, Integer expectedRuleVersion,
            ActorContext actor, SourceChannel channel) {
    }

    public record DisableRule(UUID ruleId, String reason, String accountableOwner, ActorContext actor,
            SourceChannel channel) {
    }

    public record EnableRule(UUID ruleId, String reason, ActorContext actor, SourceChannel channel) {
    }

    public record ReleaseQuarantine(UUID quarantineId, String note, ActorContext actor, SourceChannel channel) {
    }

    public record DiscardQuarantine(UUID quarantineId, String reason, ActorContext actor, SourceChannel channel) {
    }

    // ---- ingestion result -----------------------------------------------------------------------

    public enum ItemOutcome {
        ACCEPTED,
        QUARANTINED
    }

    /**
     * What happened to one reading in a message.
     *
     * @param code for a quarantined reading, the SRS error-state code the vendor and the dashboard see -
     *        {@code BMS_DEVICE_UNREGISTERED}, {@code BMS_LOCATION_UNRESOLVABLE} - or the quarantine reason
     */
    public record ItemResult(int index, String deviceCode, String channel, ItemOutcome outcome, UUID readingId,
            UUID quarantineId, QuarantineReason reason, String code) {
    }

    /**
     * @param duplicate the vendor resent a message already accepted; nothing was re-actioned and the
     *        items are the original outcome
     */
    public record IngestionResult(UUID inboxId, String sourceId, String siteCode, boolean duplicate,
            List<ItemResult> items) {

        public boolean anyQuarantined() {
            return items.stream().anyMatch(item -> item.outcome() == ItemOutcome.QUARANTINED);
        }

        public long accepted() {
            return items.stream().filter(item -> item.outcome() == ItemOutcome.ACCEPTED).count();
        }
    }
}
