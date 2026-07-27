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

    @PostMapping("/reconcile")
    public ApiResponse<ReturnReconciliation> reconcile(@Valid @RequestBody ReconcileRequest r, HttpServletRequest h) {
        EvidenceMeta evidence = r.evidenceStorageReference() == null || r.evidenceStorageReference().isBlank() ? null
                : new EvidenceMeta(r.evidenceFileName(), r.evidenceContentType(), r.evidenceStorageReference(),
                        r.evidenceSha256(), r.retentionClass(), null);
        return ApiResponse.ok(service.reconcile(new DispatchReturnService.ReconcileReturn(r.dispatchId(),
                r.expectedCount(), r.returnedCount(), r.brokenSeals(), r.notes(), evidence, actors.resolve(h),
                actors.resolveSourceChannel(h))));
    }

    @GetMapping
    public ApiResponse<List<ReturnReconciliation>> reconciliations(@RequestParam UUID dispatchId, HttpServletRequest h) {
        return ApiResponse.ok(service.reconciliations(dispatchId, actors.resolve(h)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ReturnReconciliation> detail(@PathVariable UUID id, HttpServletRequest h) {
        return ApiResponse.ok(service.reconciliation(id, actors.resolve(h)));
    }

    public record ReconcileRequest(@NotNull UUID dispatchId, Integer expectedCount,
            @PositiveOrZero int returnedCount, @PositiveOrZero int brokenSeals, String notes, String evidenceFileName,
            String evidenceContentType, String evidenceStorageReference, String evidenceSha256, String retentionClass) {}
}
