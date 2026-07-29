package gh.edu.clet.sfl.fleetlogistics.fuel.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * An immutable, policy-versioned record of one reconciliation run.
 *
 * <p>Written on every run since S168 shipped, and until now unreadable — the rows existed in
 * {@code fleet_logistics.fuel_reconciliations} with no query behind them, so a screen could report
 * that a transaction failed but never which rules it passed. This record is what makes a decision
 * reproducible: the policy and the policy version it was judged against, the outcome, the derived
 * consumption, and the full per-rule result map.
 *
 * <p>There is no mutator. A reconciliation is a fact about a moment; running it again appends a new
 * row rather than amending this one, which is why the read returns them newest first.
 */
public record FuelReconciliation(
        UUID id,
        UUID transactionId,
        UUID policyId,
        Integer policyVersion,
        String outcome,
        BigDecimal calculatedConsumption,
        Instant evaluatedAt,
        String evaluatedBy,
        Map<String, Object> ruleResults,
        String correlationId) {

    public FuelReconciliation {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(transactionId, "transactionId is required");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt is required");
        outcome = require(outcome, "outcome");
        // Copied and made unmodifiable so a caller cannot edit a stored rule result in place.
        ruleResults = ruleResults == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(ruleResults));
    }

    /** The named rules that failed, in the order the service evaluated them. */
    public java.util.List<String> failedRules() {
        return ruleResults.entrySet().stream()
                .filter(entry -> !passed(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }

    /** The named rules that passed — the half no screen could show before this record was readable. */
    public java.util.List<String> passedRules() {
        return ruleResults.entrySet().stream()
                .filter(entry -> passed(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * A rule result is stored as {@code {"passed": true|false}}.
     *
     * <p>An unrecognised shape counts as failed rather than passed: a rule whose outcome cannot be
     * read is not evidence that anything was satisfied.
     */
    private static boolean passed(Object value) {
        return value instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get("passed"));
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
