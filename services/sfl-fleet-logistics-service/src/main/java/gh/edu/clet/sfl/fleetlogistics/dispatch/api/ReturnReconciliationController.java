package gh.edu.clet.sfl.fleetlogistics.dispatch.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchEvidenceSupport.EvidenceMeta;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchReturnService;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.ReturnReconciliation;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.FleetActorResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

/** S171-06 Return-leg / reverse-logistics reconciliation against the original manifest. */
@RestController
@RequestMapping("/api/v1/dispatch/returns")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Return Reconciliation")
public class ReturnReconciliationController {

    private final DispatchReturnService service;
    private final FleetActorResolver actors;

    public ReturnReconciliationController(DispatchReturnService service, FleetActorResolver actors) {
        this.service = service;
        this.actors = actors;
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Reconciles a dispatch's return leg against the original manifest")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Request failed bean validation")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Actor lacks the required dispatch return permission for the site")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No dispatch exists with this id")
    @PostMapping("/reconcile")
    public ApiResponse<ReturnReconciliation> reconcile(@Valid @RequestBody ReconcileRequest r, HttpServletRequest h) {
        EvidenceMeta evidence = r.evidenceStorageReference() == null || r.evidenceStorageReference().isBlank() ? null
                : new EvidenceMeta(r.evidenceFileName(), r.evidenceContentType(), r.evidenceStorageReference(),
                        r.evidenceSha256(), r.retentionClass(), null);
        return ApiResponse.ok(service.reconcile(new DispatchReturnService.ReconcileReturn(r.dispatchId(),
                r.expectedCount(), r.returnedCount(), r.brokenSeals(), r.notes(), evidence, actors.resolve(h),
                actors.resolveSourceChannel(h))));
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Lists return reconciliation runs for a dispatch")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Actor lacks the required dispatch return read permission")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No dispatch exists with this id")
    @GetMapping
    public ApiResponse<List<ReturnReconciliation>> reconciliations(@RequestParam UUID dispatchId, HttpServletRequest h) {
        return ApiResponse.ok(service.reconciliations(dispatchId, actors.resolve(h)));
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Reads one return reconciliation run by id")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Actor lacks the required dispatch return read permission")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No reconciliation run exists with this id")
    @GetMapping("/{id}")
    public ApiResponse<ReturnReconciliation> detail(@PathVariable UUID id, HttpServletRequest h) {
        return ApiResponse.ok(service.reconciliation(id, actors.resolve(h)));
    }

    public record ReconcileRequest(@NotNull UUID dispatchId, Integer expectedCount,
            @PositiveOrZero int returnedCount, @PositiveOrZero int brokenSeals, String notes, String evidenceFileName,
            String evidenceContentType, String evidenceStorageReference, String evidenceSha256, String retentionClass) {}
}
