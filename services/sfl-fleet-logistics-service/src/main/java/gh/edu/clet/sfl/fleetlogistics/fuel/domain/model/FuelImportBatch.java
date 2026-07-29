package gh.edu.clet.sfl.fleetlogistics.fuel.domain.model;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A CSV import, file-level, with its retained row outcomes.
 *
 * <p>Written on every upload since S168 shipped and, until now, readable only in the response to the
 * upload itself — so an operator who navigated away lost the record of which rows were rejected and
 * why. The rows are the point: a batch is never rejected as a whole for one bad row, and
 * {@code rejectedRows} is a count of individual failures each carrying its own reason.
 *
 * <p>{@code fileHash} is what makes a re-import detectable, and it is why
 * {@code uq_fuel_import_file} exists on {@code (site_code, source_system, file_hash)}.
 */
public record FuelImportBatch(
        UUID id,
        SiteCode siteCode,
        String sourceSystem,
        String fileName,
        String fileHash,
        Status status,
        int totalRows,
        int acceptedRows,
        int rejectedRows,
        String submittedBy,
        Instant submittedAt,
        String correlationId,
        /** Empty on a list read; populated on a detail read. */
        List<FuelImportRow> rows) {

    public enum Status { COMPLETED, COMPLETED_WITH_ERRORS }

    public FuelImportBatch {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(siteCode, "siteCode is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(submittedAt, "submittedAt is required");
        sourceSystem = require(sourceSystem, "sourceSystem");
        fileName = require(fileName, "fileName");
        fileHash = require(fileHash, "fileHash");
        if (totalRows < 0 || acceptedRows < 0 || rejectedRows < 0) {
            throw new IllegalArgumentException("import row counts cannot be negative");
        }
        rows = rows == null ? List.of() : List.copyOf(rows);
    }

    /** Derived rather than stored, so it cannot disagree with the counts it is read from. */
    public boolean fullyAccepted() {
        return rejectedRows == 0;
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
