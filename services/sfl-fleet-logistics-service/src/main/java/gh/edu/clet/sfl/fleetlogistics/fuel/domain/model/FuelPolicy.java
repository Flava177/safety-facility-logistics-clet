package gh.edu.clet.sfl.fleetlogistics.fuel.domain.model;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Effective-dated, reproducible fuel rules. Unknown institutional values stay configuration data. */
public record FuelPolicy(UUID id, SiteCode siteCode, String name, Instant effectiveFrom, Instant effectiveTo,
        int policyVersion, BigDecimal maxPerTransaction, BigDecimal dailyLimit, BigDecimal monthlyLimit,
        BigDecimal tankCapacity, BigDecimal minConsumption, BigDecimal maxConsumption,
        long odometerJumpTolerance, boolean receiptRequired, int receiptGraceHours, BigDecimal materialityAmount,
        int anomalySlaHours, BigDecimal costVarianceTolerance, int repeatedPatternWindowHours,
        int repeatedPatternThreshold, Set<String> allowedFuelProducts, Set<String> approvedVendors, Status status,
        RecordMetadata metadata) {

    public static final BigDecimal DEFAULT_COST_VARIANCE_TOLERANCE = new BigDecimal("0.30");
    public static final int DEFAULT_REPEATED_PATTERN_WINDOW_HOURS = 720;
    public static final int DEFAULT_REPEATED_PATTERN_THRESHOLD = 3;

    public enum Status { ACTIVE, INACTIVE, ARCHIVED }

    public FuelPolicy {
        Objects.requireNonNull(id); Objects.requireNonNull(siteCode); Objects.requireNonNull(effectiveFrom);
        Objects.requireNonNull(maxPerTransaction); Objects.requireNonNull(materialityAmount);
        Objects.requireNonNull(status); Objects.requireNonNull(metadata);
        costVarianceTolerance = costVarianceTolerance == null ? DEFAULT_COST_VARIANCE_TOLERANCE : costVarianceTolerance;
        name = require(name, "name");
        if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) throw new IllegalArgumentException("effectiveTo must follow effectiveFrom");
        if (policyVersion < 1 || maxPerTransaction.signum() <= 0 || odometerJumpTolerance < 0
                || receiptGraceHours < 0 || materialityAmount.signum() < 0 || anomalySlaHours < 1
                || costVarianceTolerance.signum() < 0 || repeatedPatternWindowHours < 1
                || repeatedPatternThreshold < 1) {
            throw new IllegalArgumentException("fuel policy limits are invalid");
        }
        allowedFuelProducts = normalized(allowedFuelProducts);
        approvedVendors = normalized(approvedVendors);
    }

    public FuelPolicy(UUID id, SiteCode siteCode, String name, Instant effectiveFrom, Instant effectiveTo,
            int policyVersion, BigDecimal maxPerTransaction, BigDecimal dailyLimit, BigDecimal monthlyLimit,
            BigDecimal tankCapacity, BigDecimal minConsumption, BigDecimal maxConsumption,
            long odometerJumpTolerance, boolean receiptRequired, int receiptGraceHours, BigDecimal materialityAmount,
            int anomalySlaHours, Set<String> allowedFuelProducts, Set<String> approvedVendors, Status status,
            RecordMetadata metadata) {
        this(id, siteCode, name, effectiveFrom, effectiveTo, policyVersion, maxPerTransaction, dailyLimit,
                monthlyLimit, tankCapacity, minConsumption, maxConsumption, odometerJumpTolerance, receiptRequired,
                receiptGraceHours, materialityAmount, anomalySlaHours, DEFAULT_COST_VARIANCE_TOLERANCE,
                DEFAULT_REPEATED_PATTERN_WINDOW_HOURS, DEFAULT_REPEATED_PATTERN_THRESHOLD, allowedFuelProducts,
                approvedVendors, status, metadata);
    }

    public boolean appliesAt(Instant at) {
        return status == Status.ACTIVE && !at.isBefore(effectiveFrom) && (effectiveTo == null || at.isBefore(effectiveTo));
    }

    public boolean allowsProduct(String product) { return allowedFuelProducts.isEmpty() || allowedFuelProducts.contains(product.toUpperCase()); }
    public boolean allowsVendor(String vendor) { return approvedVendors.isEmpty() || approvedVendors.contains(vendor.toUpperCase()); }

    /**
     * Whether {@code other} would judge a transaction exactly as this policy does.
     *
     * <h2>What this is for</h2>
     *
     * <p>Every reconciliation records the policy id <em>and</em> the {@code policyVersion} that
     * judged it, and that pair is the only account anyone has of the rules a past decision was made
     * under - the run does not copy the limits it applied. So the pair has to identify one rule set
     * and go on identifying it. Revising a limit while leaving the version alone breaks that
     * quietly: "policy 7 version 1" then means one thing for judgements made before the edit and
     * another for those made after, and nothing in the record says which.
     *
     * <p>Compared field by field rather than with {@code equals}, because the record's own equality
     * includes {@code name}, the effective period and the audit metadata - none of which changes how
     * a transaction is judged. Renaming a policy or correcting its description must stay free.
     *
     * <p>The effective period is deliberately excluded too. It decides <em>which</em> policy applies
     * to a transaction, not what that policy does, and a completed run has already recorded its
     * outcome; the overlap rule is what governs the period.
     *
     * <p>Scale-insensitive on the money and quantity fields: {@code BigDecimal.equals} calls 50 and
     * 50.00 different, which would report a change that is not one and force a pointless version.
     */
    public boolean hasSameRulesAs(FuelPolicy other) {
        return sameNumber(maxPerTransaction, other.maxPerTransaction)
                && sameNumber(dailyLimit, other.dailyLimit)
                && sameNumber(monthlyLimit, other.monthlyLimit)
                && sameNumber(tankCapacity, other.tankCapacity)
                && sameNumber(minConsumption, other.minConsumption)
                && sameNumber(maxConsumption, other.maxConsumption)
                && sameNumber(materialityAmount, other.materialityAmount)
                && sameNumber(costVarianceTolerance, other.costVarianceTolerance)
                && odometerJumpTolerance == other.odometerJumpTolerance
                && receiptRequired == other.receiptRequired
                && receiptGraceHours == other.receiptGraceHours
                && anomalySlaHours == other.anomalySlaHours
                && repeatedPatternWindowHours == other.repeatedPatternWindowHours
                && repeatedPatternThreshold == other.repeatedPatternThreshold
                && allowedFuelProducts.equals(other.allowedFuelProducts)
                && approvedVendors.equals(other.approvedVendors);
    }

    private static boolean sameNumber(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }

    private static Set<String> normalized(Set<String> values) {
        return values == null ? Set.of() : values.stream().filter(Objects::nonNull).map(String::strip)
                .filter(v -> !v.isEmpty()).map(String::toUpperCase).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.strip();
    }
}
