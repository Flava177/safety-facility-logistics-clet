package gh.edu.clet.sfl.fleetlogistics.dispatch.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchScanService;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.ScanImportBatch;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.ScanImportRow;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.FleetActorResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** S171-04 optional scanner ingestion: idempotent CSV scan-batch import and per-row results. */
@RestController
@RequestMapping("/api/v1/dispatch/scans")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Dispatch Integrations")
public class DispatchScanController {

    private final DispatchScanService service;
    private final FleetActorResolver actors;

    public DispatchScanController(DispatchScanService service, FleetActorResolver actors) {
        this.service = service;
        this.actors = actors;
    }

    @PostMapping(value = "/imports", consumes = "multipart/form-data")
    public ApiResponse<ScanImportBatch> importCsv(@RequestParam String siteCode, @RequestParam String sourceSystem,
            @RequestParam(required = false) String batchReference, @RequestParam(required = false) UUID dispatchId,
            @RequestPart MultipartFile file, HttpServletRequest h) throws IOException {
        return ApiResponse.ok(service.importCsv(new DispatchScanService.ImportScanBatch(siteCode, sourceSystem,
                batchReference, dispatchId, file.getBytes(), actors.resolve(h), actors.resolveSourceChannel(h))));
    }

    /** The site's scan batches, newest first. Closes gap 3. */
    @GetMapping("/imports")
    public ApiResponse<DispatchPageResponse<ScanImportBatch>> batches(@RequestParam String siteCode,
            @RequestParam(required = false) String sourceSystem,
            @RequestParam(required = false) UUID dispatchId,
            @RequestParam(required = false) ScanImportBatch.Status status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String sort, HttpServletRequest h) {
        return ApiResponse.ok(DispatchPageResponse.of(service.batches(siteCode, sourceSystem, dispatchId, status,
                DispatchPageResponse.paging(page, size, sort), actors.resolve(h))));
    }

    @GetMapping("/imports/{id}")
    public ApiResponse<ScanImportBatch> batch(@PathVariable UUID id, HttpServletRequest h) {
        return ApiResponse.ok(service.batch(id, actors.resolve(h)));
    }

    @GetMapping("/imports/{id}/rows")
    public ApiResponse<List<ScanImportRow>> rows(@PathVariable UUID id, HttpServletRequest h) {
        return ApiResponse.ok(service.rows(id, actors.resolve(h)));
    }
}
