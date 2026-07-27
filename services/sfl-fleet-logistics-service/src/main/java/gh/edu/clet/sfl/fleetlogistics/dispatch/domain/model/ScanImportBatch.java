package gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import java.util.Objects;
import java.util.UUID;

/** File-level result of an optional scanner/barcode batch ingestion, with idempotent per-row outcomes. */
public record ScanImportBatch(UUID id, SiteCode siteCode, String batchReference, String sourceSystem, UUID dispatchId,
        int totalRows, int acceptedRows, int mismatchRows, Status status, RecordMetadata metadata) {

    public enum Status { PROCESSED, PARTIAL, FAILED }

    public ScanImportBatch {
        Objects.requireNonNull(id); Objects.requireNonNull(siteCode); Objects.requireNonNull(status);
        Objects.requireNonNull(metadata);
        batchReference = require(batchReference, "batchReference");
        sourceSystem = require(sourceSystem, "sourceSystem");
        if (totalRows < 0 || acceptedRows < 0 || mismatchRows < 0) {
            throw new IllegalArgumentException("scan batch counts cannot be negative");
        }
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.strip();
    }
}
