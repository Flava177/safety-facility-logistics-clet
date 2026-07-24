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
        int anomalySlaHours, Set<String> allowedFuelProducts, Set<String> approvedVendors, Status status,
        RecordMetadata metadata) {

    public enum Status { ACTIVE, INACTIVE, ARCHIVED }

    public FuelPolicy {
        Objects.requireNonNull(id); Objects.requireNonNull(siteCode); Objects.requireNonNull(effectiveFrom);
        Objects.requireNonNull(maxPerTransaction); Objects.requireNonNull(materialityAmount);
        Objects.requireNonNull(status); Objects.requireNonNull(metadata);
        name = require(name, "name");
        if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) throw new IllegalArgumentException("effectiveTo must follow effectiveFrom");
        if (policyVersion < 1 || maxPerTransaction.signum() <= 0 || odometerJumpTolerance < 0
                || receiptGraceHours < 0 || materialityAmount.signum() < 0 || anomalySlaHours < 1) {
            throw new IllegalArgumentException("fuel policy limits are invalid");
        }
        allowedFuelProducts = normalized(allowedFuelProducts);
        approvedVendors = normalized(approvedVendors);
    }

    public boolean appliesAt(Instant at) {
        return status == Status.ACTIVE && !at.isBefore(effectiveFrom) && (effectiveTo == null || at.isBefore(effectiveTo));
    }

    public boolean allowsProduct(String product) { return allowedFuelProducts.isEmpty() || allowedFuelProducts.contains(product.toUpperCase()); }
    public boolean allowsVendor(String vendor) { return approvedVendors.isEmpty() || approvedVendors.contains(vendor.toUpperCase()); }

    private static Set<String> normalized(Set<String> values) {
        return values == null ? Set.of() : values.stream().filter(Objects::nonNull).map(String::strip)
                .filter(v -> !v.isEmpty()).map(String::toUpperCase).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.strip();
    }
}
