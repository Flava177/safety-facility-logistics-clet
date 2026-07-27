package gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A single scanned code within a batch, classified against the manifest. */
public record ScanImportRow(UUID id, UUID batchId, SiteCode siteCode, String rowReference, String scannedCode,
        UUID courierItemId, Outcome outcome, String message, Instant createdAt) {

    public enum Outcome { MATCHED, MISMATCH, UNREGISTERED }

    public ScanImportRow {
        Objects.requireNonNull(id); Objects.requireNonNull(batchId); Objects.requireNonNull(siteCode);
        Objects.requireNonNull(outcome); Objects.requireNonNull(createdAt);
        rowReference = require(rowReference, "rowReference");
        scannedCode = require(scannedCode, "scannedCode");
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.strip();
    }
}
