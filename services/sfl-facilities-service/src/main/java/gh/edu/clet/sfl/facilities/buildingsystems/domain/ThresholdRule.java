package gh.edu.clet.sfl.facilities.buildingsystems.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * One version of a threshold or fault-condition rule - SRS-SFL-S156-02.
 *
 * <h2>Versioned, never edited</h2>
 *
 * <p>"Rule changes are versioned and audited." Each change produces a new row with {@code ruleVersion}
 * one higher; the previous row is stamped {@code supersededAt} and kept. {@code ruleId} is stable across
 * versions and {@code id} names one version, so an alert can record exactly which version of which rule
 * raised it - the question an investigator asks after somebody moved a threshold the week before.
 *
 * <h2>Scope</h2>
 *
 * <p>A rule applies to one {@link MeasuredQuantity} and is narrowed by any of system type, device,
 * building and room ("per system/sensor type and location"). A null scope field means "any". A rule with
 * every scope field null applies to that quantity across the site.
 *
 * <h2>Disabling</h2>
 *
 * <p>"A rule cannot be silently disabled without an audited override and a named accountable owner." A
 * rule is disabled only through {@link #disable}, which refuses without both; {@link #revise} cannot
 * touch {@code enabled} at all, so there is no second route that forgets to ask.
 *
 * @param debounce how long a breach must persist before it is real. Zero is allowed and means "raise on
 *        the first breaching reading" - for a rule where one reading is already enough
 */
public record ThresholdRule(
        UUID id,
        UUID ruleId,
        int ruleVersion,
        String siteCode,
        String name,
        MeasuredQuantity quantity,
        BuildingSystemType systemType,
        UUID deviceId,
        String buildingCode,
        UUID roomId,
        RuleCondition condition,
        BigDecimal lowerLimit,
        BigDecimal upperLimit,
        Set<Integer> codes,
        Duration debounce,
        AlertPriority priority,
        boolean enabled,
        String changeReason,
        String overrideReason,
        String accountableOwner,
        Instant supersededAt,
        RecordMetadata metadata) {

    public ThresholdRule {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(ruleId, "ruleId is required");
        siteCode = EstateCodes.normalize(siteCode);
        EstateCodes.require(name, "name");
        name = name.strip();
        Objects.requireNonNull(quantity, "quantity is required");
        buildingCode = buildingCode == null || buildingCode.isBlank() ? null : EstateCodes.normalize(buildingCode);
        Objects.requireNonNull(condition, "condition is required");
        codes = codes == null ? Set.of() : Set.copyOf(codes);
        Objects.requireNonNull(debounce, "debounce is required");
        Objects.requireNonNull(priority, "priority is required");
        Objects.requireNonNull(metadata, "metadata is required");
        validateCondition(condition, lowerLimit, upperLimit, codes, debounce);
    }

    public static ThresholdRule create(UUID id, UUID ruleId, String siteCode, String name, MeasuredQuantity quantity,
            BuildingSystemType systemType, UUID deviceId, String buildingCode, UUID roomId, RuleCondition condition,
            BigDecimal lowerLimit, BigDecimal upperLimit, Set<Integer> codes, Duration debounce, AlertPriority priority,
            String changeReason, String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new ThresholdRule(id, ruleId, 1, siteCode, name, quantity, systemType, deviceId, buildingCode, roomId,
                condition, lowerLimit, upperLimit, codes, debounce, priority, true, EstateCodes.blankToNull(changeReason),
                null, null, null, RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    public boolean isCurrent() {
        return supersededAt == null;
    }

    public boolean isActive() {
        return enabled && isCurrent();
    }

    /** The next version with new thresholds. Cannot change {@code enabled} - see the class comment. */
    public ThresholdRule revise(UUID versionId, String newName, BuildingSystemType newSystemType, UUID newDeviceId,
            String newBuildingCode, UUID newRoomId, RuleCondition newCondition, BigDecimal newLower, BigDecimal newUpper,
            Set<Integer> newCodes, Duration newDebounce, AlertPriority newPriority, String reason, String actorId,
            Instant at, SourceChannel channel, String correlationId) {
        requireCurrent();
        EstateCodes.require(reason, "reason");
        return new ThresholdRule(versionId, ruleId, ruleVersion + 1, siteCode, newName, quantity, newSystemType,
                newDeviceId, newBuildingCode, newRoomId, newCondition, newLower, newUpper, newCodes, newDebounce,
                newPriority, enabled, reason.strip(), overrideReason, accountableOwner, null,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /**
     * The audited override - SRS-SFL-S156-02 validation.
     *
     * <p>Refused with {@code BMS_RULE_OVERRIDE_REQUIRED} unless both a reason and a named accountable owner
     * are given. The owner is a named person, not the actor: the director who switches a rule off during a
     * chiller replacement names the engineer who will switch it back on.
     */
    public ThresholdRule disable(UUID versionId, String reason, String owner, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        requireCurrent();
        if (reason == null || reason.isBlank() || owner == null || owner.isBlank()) {
            throw new FacilitiesException(FacilitiesErrorCode.BMS_RULE_OVERRIDE_REQUIRED);
        }
        if (!enabled) {
            throw new FacilitiesException.InvalidStateTransitionException("Rule " + ruleId + " is already disabled");
        }
        return new ThresholdRule(versionId, ruleId, ruleVersion + 1, siteCode, name, quantity, systemType, deviceId,
                buildingCode, roomId, condition, lowerLimit, upperLimit, codes, debounce, priority, false,
                "Disabled by audited override: " + reason.strip(), reason.strip(), owner.strip(), null,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    public ThresholdRule enable(UUID versionId, String reason, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        requireCurrent();
        EstateCodes.require(reason, "reason");
        if (enabled) {
            throw new FacilitiesException.InvalidStateTransitionException("Rule " + ruleId + " is already enabled");
        }
        return new ThresholdRule(versionId, ruleId, ruleVersion + 1, siteCode, name, quantity, systemType, deviceId,
                buildingCode, roomId, condition, lowerLimit, upperLimit, codes, debounce, priority, true, reason.strip(),
                null, null, null, RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** Stamps this version as replaced. The row stays; only its currency changes. */
    public ThresholdRule supersede(Instant at) {
        return new ThresholdRule(id, ruleId, ruleVersion, siteCode, name, quantity, systemType, deviceId, buildingCode,
                roomId, condition, lowerLimit, upperLimit, codes, debounce, priority, enabled, changeReason,
                overrideReason, accountableOwner, at, metadata);
    }

    /** The codes as the comma-separated text the migration stores. */
    public String codesText() {
        return codes.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }

    private void requireCurrent() {
        if (!isCurrent()) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Version " + ruleVersion + " of rule " + ruleId + " has been superseded; change the current version");
        }
    }

    private static void validateCondition(RuleCondition condition, BigDecimal lower, BigDecimal upper,
            Set<Integer> codes, Duration debounce) {
        if (debounce.isNegative()) {
            throw new FacilitiesException.ValidationFailedException("debounce cannot be negative");
        }
        if (condition.usesLower() && lower == null) {
            throw new FacilitiesException.ValidationFailedException(condition + " requires lowerLimit");
        }
        if (condition.usesUpper() && upper == null) {
            throw new FacilitiesException.ValidationFailedException(condition + " requires upperLimit");
        }
        if (condition == RuleCondition.OUTSIDE_BAND && lower.compareTo(upper) >= 0) {
            throw new FacilitiesException.ValidationFailedException("lowerLimit must be below upperLimit");
        }
        if (condition == RuleCondition.CODE_MATCH && codes.isEmpty()) {
            throw new FacilitiesException.ValidationFailedException("CODE_MATCH requires at least one code");
        }
    }
}
