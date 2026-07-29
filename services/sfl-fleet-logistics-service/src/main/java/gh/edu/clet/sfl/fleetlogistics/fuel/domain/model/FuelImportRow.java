package gh.edu.clet.sfl.fleetlogistics.fuel.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * One row of a CSV import and what became of it.
 *
 * <p>{@code rowNumber} is the line in the file the operator uploaded, counting the header as line
 * one — so it matches what they see when they open the file to fix it. An accepted row carries the
 * transaction it created; a rejected one carries the service's own error code and message, retained
 * exactly as the capture command raised them.
 */
public record FuelImportRow(
        UUID id,
        int rowNumber,
        Status status,
        UUID transactionId,
        String errorCode,
        String errorMessage) {

    public enum Status { ACCEPTED, REJECTED }

    public FuelImportRow {
        Objects.requireNonNull(status, "status is required");
        if (rowNumber < 1) {
            throw new IllegalArgumentException("rowNumber is one-based");
        }
    }

    public boolean accepted() {
        return status == Status.ACCEPTED;
    }
}
