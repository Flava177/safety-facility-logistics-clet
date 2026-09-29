package gh.edu.clet.sfl.facilities.buildingsystems.api;

import gh.edu.clet.sfl.facilities.buildingsystems.domain.AlertPriority;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BuildingSystemType;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.DeviceKind;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.MeasuredQuantity;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.RuleCondition;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** The S156 request bodies, validated the way {@code BookingRequests} is. */
public final class BuildingSystemsRequests {

    private BuildingSystemsRequests() {
    }

    /** @param avampAssetId must already exist, active, in this site's AVAMP projection (S156-04) */
    public record RegisterDevice(
            @NotBlank @Size(max = 40) String siteCode,
            @NotBlank @Size(max = 120) String deviceCode,
            @NotBlank @Size(max = 120) String avampAssetId,
            @NotBlank @Size(max = 200) String name,
            @NotNull BuildingSystemType systemType,
            @NotNull DeviceKind kind,
            @NotBlank @Size(max = 80) String buildingCode,
            UUID roomId,
            @Min(1) int expectedIntervalSeconds,
            LocalDate installedOn,
            @Size(max = 80) String firmwareVersion,
            LocalDate firmwareReviewDueOn,
            LocalDate warrantyExpiresOn,
            LocalDate calibrationDueOn) {
    }

    public record ReviseDevice(
            @Size(max = 200) String name,
            @Size(max = 80) String buildingCode,
            UUID roomId,
            int expectedIntervalSeconds,
            LocalDate installedOn,
            @Size(max = 80) String firmwareVersion,
            LocalDate firmwareReviewDueOn,
            LocalDate warrantyExpiresOn,
            LocalDate calibrationDueOn,
            Long expectedVersion) {
    }

    public record RetireDevice(@NotBlank @Size(max = 1000) String reason, Long expectedVersion) {
    }

    public record CreateRule(
            @NotBlank @Size(max = 40) String siteCode,
            @NotBlank @Size(max = 200) String name,
            @NotNull MeasuredQuantity quantity,
            BuildingSystemType systemType,
            UUID deviceId,
            @Size(max = 80) String buildingCode,
            UUID roomId,
            @NotNull RuleCondition condition,
            BigDecimal lowerLimit,
            BigDecimal upperLimit,
            Set<Integer> codes,
            Duration debounce,
            @NotNull AlertPriority priority,
            @NotBlank @Size(max = 1000) String reason) {
    }

    public record ReviseRule(
            @Size(max = 200) String name,
            BuildingSystemType systemType,
            UUID deviceId,
            @Size(max = 80) String buildingCode,
            UUID roomId,
            RuleCondition condition,
            BigDecimal lowerLimit,
            BigDecimal upperLimit,
            Set<Integer> codes,
            Duration debounce,
            AlertPriority priority,
            @NotBlank @Size(max = 1000) String reason,
            Integer expectedRuleVersion) {
    }

    /** @param accountableOwner a named person, not the caller - SRS-SFL-S156-02 validation */
    public record DisableRule(
            @NotBlank @Size(max = 1000) String reason,
            @NotBlank @Size(max = 160) String accountableOwner) {
    }

    public record EnableRule(@NotBlank @Size(max = 1000) String reason) {
    }

    public record ReleaseQuarantine(@Size(max = 1000) String note) {
    }

    public record DiscardQuarantine(@NotBlank @Size(max = 1000) String reason) {
    }
}
