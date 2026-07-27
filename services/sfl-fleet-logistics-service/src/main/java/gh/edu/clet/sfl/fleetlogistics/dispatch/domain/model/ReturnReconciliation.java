package gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Reconciliation of a dispatch return leg against the original manifest. A shortfall, extra or broken
 * seal yields a DISCREPANCY outcome, which blocks custody closure until an exception case resolves it.
 */
public record ReturnReconciliation(UUID id, UUID dispatchId, SiteCode siteCode, int expectedCount, int returnedCount,
        int shortfall, int extras, int brokenSeals, ReturnOutcome outcome, String notes, UUID evidenceId,
        String reconciledBy, Instant reconciledAt, RecordMetadata metadata) {

    public enum ReturnOutcome { MATCHED, DISCREPANCY }

    public ReturnReconciliation {
        Objects.requireNonNull(id); Objects.requireNonNull(dispatchId); Objects.requireNonNull(siteCode);
        Objects.requireNonNull(outcome); Objects.requireNonNull(reconciledAt); Objects.requireNonNull(metadata);
        reconciledBy = require(reconciledBy, "reconciledBy");
        if (expectedCount < 0 || returnedCount < 0 || shortfall < 0 || extras < 0 || brokenSeals < 0) {
            throw new IllegalArgumentException("reconciliation counts cannot be negative");
        }
        if (outcome == ReturnOutcome.MATCHED && (shortfall > 0 || extras > 0 || brokenSeals > 0)) {
            throw new IllegalArgumentException("A matched reconciliation cannot report a discrepancy");
        }
    }

    public boolean matched() { return outcome == ReturnOutcome.MATCHED; }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.strip();
    }
}
