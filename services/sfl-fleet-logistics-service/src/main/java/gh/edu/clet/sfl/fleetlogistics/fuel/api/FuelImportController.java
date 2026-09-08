package gh.edu.clet.sfl.fleetlogistics.fuel.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.FleetActorResolver;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.service.FuelApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.service.FuelImportService;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelImportBatch;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelImportRow;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/fuel/imports")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Fuel Integrations")
public class FuelImportController {
    private final FuelImportService imports;
    private final FuelApplicationService service;
    private final FleetActorResolver actors;

    public FuelImportController(
            FuelImportService i, FuelApplicationService s, FleetActorResolver a) {
        imports = i;
        service = s;
        actors = a;
    }

    /**
     * Imports a CSV. A re-uploaded file is refused with {@code FUEL_IMPORT_ALREADY_PROCESSED}
     * before any row is captured, rather than after - the unique constraint used to fire at the end
     * and escape as an unmapped 500.
     */
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Imports a CSV of fuel transactions, capturing each row as a transaction")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "The file is missing a header/row, or exceeds the bulk-import size limit")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Actor lacks FUEL_TRANSACTION_IMPORT for the site")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "This exact file was already imported for the site and source system")
    @PostMapping(value = "/csv", consumes = "multipart/form-data")
    public ApiResponse<FuelImportService.ImportResult> csv(
            @RequestParam String siteCode,
            @RequestParam String sourceSystem,
            @RequestPart MultipartFile file,
            HttpServletRequest h)
            throws IOException {
        return ApiResponse.ok(
                imports.importCsv(
                        siteCode,
                        sourceSystem,
                        file.getOriginalFilename(),
                        file.getBytes(),
                        actors.resolve(h)));
    }

    /**
     * Past import batches for the site.
     *
     * <p>The batches and their rows have been written since the first release with nothing reading
     * them, so an operator who navigated away from the upload lost the record of which rows were
     * rejected and why. Headers only here; the rows come with the detail read.
     */
    @io.swagger.v3.oas.annotations.Operation(summary = "Lists past fuel import batches for a site")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Actor lacks FUEL_TRANSACTION_READ for the site")
    @GetMapping
    public ApiResponse<FuelPageResponse<FuelImportBatch>> list(
            @RequestParam String siteCode,
            @RequestParam(required = false) String sourceSystem,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String sort,
            HttpServletRequest h) {
        return ApiResponse.ok(
                FuelPageResponse.of(
                        service.importBatches(
                                siteCode,
                                sourceSystem,
                                FuelPageResponse.paging(page, size, sort),
                                actors.resolve(h))));
    }

    /** One batch with every row outcome and its retained validation error. */
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Reads one fuel import batch with every row outcome")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Actor lacks FUEL_TRANSACTION_READ for the batch's site")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "No import batch exists with this id")
    @GetMapping("/{id}")
    public ApiResponse<FuelImportBatch> detail(@PathVariable UUID id, HttpServletRequest h) {
        return ApiResponse.ok(service.importBatch(id, actors.resolve(h)));
    }

    /**
     * The batch's rows, paged.
     *
     * <p>The detail read above carries every row, which a file of thousands makes unusable. This is
     * the same rows with a page around them, and with the one filter that matters: {@code status},
     * so "show me what was rejected" is a query rather than a scroll.
     */
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Lists a fuel import batch's rows, paged and optionally filtered by status")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Actor lacks FUEL_TRANSACTION_READ for the batch's site")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "No import batch exists with this id")
    @GetMapping("/{id}/rows")
    public ApiResponse<FuelPageResponse<FuelImportRow>> rows(
            @PathVariable UUID id,
            @RequestParam(required = false) FuelImportRow.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sort,
            HttpServletRequest h) {
        return ApiResponse.ok(
                FuelPageResponse.of(
                        service.importRows(
                                id,
                                status,
                                FuelPageResponse.paging(page, size, sort),
                                actors.resolve(h))));
    }
}
