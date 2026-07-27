package gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A destination receipt confirmation verifying seal integrity, item count and recipient signature against
 * the manifest. Edge-captured receipts carry a client capture time and correlation id so an offline
 * capture reconciles idempotently on restore without loss or double-apply.
 */
public record DispatchReceipt(UUID id, UUID dispatchId, SiteCode siteCode, SealState sealState, boolean sealVerified,
        int expectedCount, int verifiedCount, String recipientName, UUID signatureEvidenceId, ReceiptOutcome outcome,
        VarianceType varianceType, Instant capturedAt, boolean edgeCaptured, String captureCorrelationId,
        Instant reconciledAt, RecordMetadata metadata) {

    public enum ReceiptOutcome { CLEAN, VARIANCE }
    public enum VarianceType { BROKEN_SEAL, SHORT_COUNT, OVER_COUNT, WRONG_RECIPIENT, MISSING_SIGNATURE }

    public DispatchReceipt {
        Objects.requireNonNull(id); Objects.requireNonNull(dispatchId); Objects.requireNonNull(siteCode);
        Objects.requireNonNull(sealState); Objects.requireNonNull(outcome); Objects.requireNonNull(capturedAt);
        Objects.requireNonNull(metadata);
        recipientName = require(recipientName, "recipientName");
        captureCorrelationId = require(captureCorrelationId, "captureCorrelationId");
        if (expectedCount < 0 || verifiedCount < 0) throw new IllegalArgumentException("counts cannot be negative");
        if (outcome == ReceiptOutcome.VARIANCE && varianceType == null) {
            throw new IllegalArgumentException("A variance receipt requires a variance type");
        }
        if (outcome == ReceiptOutcome.CLEAN && varianceType != null) {
            throw new IllegalArgumentException("A clean receipt cannot carry a variance type");
        }
    }

    public boolean clean() { return outcome == ReceiptOutcome.CLEAN; }

    /** Security-relevant variances (seal/tamper) must be surfaced to SSEMP. */
    public boolean securityRelevant() {
        return outcome == ReceiptOutcome.VARIANCE
                && (varianceType == VarianceType.BROKEN_SEAL || sealState.isCompromised());
    }

    public DispatchReceipt reconciled(Instant at, RecordMetadata changed) {
        return new DispatchReceipt(id, dispatchId, siteCode, sealState, sealVerified, expectedCount, verifiedCount,
                recipientName, signatureEvidenceId, outcome, varianceType, capturedAt, edgeCaptured,
                captureCorrelationId, Objects.requireNonNull(at, "reconciledAt"), changed);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.strip();
    }
}
