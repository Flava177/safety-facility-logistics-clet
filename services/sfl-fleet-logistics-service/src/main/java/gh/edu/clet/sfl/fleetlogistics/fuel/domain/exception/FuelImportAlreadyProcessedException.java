package gh.edu.clet.sfl.fleetlogistics.fuel.domain.exception;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetDomainException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetErrorCode;
import java.util.Map;
import java.util.UUID;

/**
 * The same file content re-imported for one site and source system.
 *
 * <p>{@code uq_fuel_import_file} on {@code (site_code, source_system, file_hash)} already refused
 * this; what was missing was a mapped error, so the duplicate surfaced as an unhandled 500 with
 * Spring's default body. Nothing is duplicated when it happens — every row goes through the
 * idempotent capture command first — and the message says so.
 */
public class FuelImportAlreadyProcessedException extends FleetDomainException {

    public FuelImportAlreadyProcessedException(Map<String, Object> details) {
        super(FleetErrorCode.FUEL_IMPORT_ALREADY_PROCESSED, details);
    }

    public static FuelImportAlreadyProcessedException of(String siteCode, String sourceSystem,
            String fileName, String fileHash, UUID existingBatchId) {
        return new FuelImportAlreadyProcessedException(Map.of(
                "siteCode", siteCode,
                "sourceSystem", sourceSystem,
                "fileName", fileName == null ? "" : fileName,
                "fileHash", fileHash,
                "existingBatchId", existingBatchId == null ? "" : existingBatchId.toString()));
    }
}
