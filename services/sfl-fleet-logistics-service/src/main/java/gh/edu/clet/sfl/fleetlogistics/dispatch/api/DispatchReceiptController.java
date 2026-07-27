package gh.edu.clet.sfl.fleetlogistics.dispatch.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchEvidenceSupport.EvidenceMeta;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchReceiptService;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchReceipt;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.SealState;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.FleetActorResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

/** S171-03 Destination receipt confirmation and variance handling (edge-capable, idempotent). */
@RestController
@RequestMapping("/api/v1/dispatch/receipts")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Dispatch Receipts")
public class DispatchReceiptController {

    private final DispatchReceiptService service;
    private final FleetActorResolver actors;

    public DispatchReceiptController(DispatchReceiptService service, FleetActorResolver actors) {
        this.service = service;
        this.actors = actors;
    }

    @PostMapping
    public ApiResponse<DispatchReceipt> confirm(@Valid @RequestBody ConfirmReceiptRequest r, HttpServletRequest h) {
        EvidenceMeta signature = r.signatureStorageReference() == null || r.signatureStorageReference().isBlank() ? null
                : new EvidenceMeta(r.signatureFileName(), r.signatureContentType(), r.signatureStorageReference(),
                        r.signatureSha256(), r.retentionClass(), null);
        return ApiResponse.ok(service.confirmReceipt(new DispatchReceiptService.ConfirmReceipt(r.dispatchId(),
                r.sealState(), r.sealVerified(), r.expectedCount(), r.verifiedCount(), r.recipientName(),
                r.expectedRecipient(), signature, r.captureCorrelationId(), r.edgeCaptured(), r.capturedAt(),
                actors.resolve(h), actors.resolveSourceChannel(h))));
    }

    @GetMapping
    public ApiResponse<List<DispatchReceipt>> receipts(@RequestParam UUID dispatchId, HttpServletRequest h) {
        return ApiResponse.ok(service.receipts(dispatchId, actors.resolve(h)));
    }

    @GetMapping("/{id}")
    public ApiResponse<DispatchReceipt> detail(@PathVariable UUID id, HttpServletRequest h) {
        return ApiResponse.ok(service.receipt(id, actors.resolve(h)));
    }

    public record ConfirmReceiptRequest(@NotNull UUID dispatchId, @NotNull SealState sealState, boolean sealVerified,
            Integer expectedCount, @PositiveOrZero int verifiedCount, @NotBlank String recipientName,
            String expectedRecipient, String captureCorrelationId, boolean edgeCaptured, Instant capturedAt,
            String signatureFileName, String signatureContentType, String signatureStorageReference,
            String signatureSha256, String retentionClass) {}
}
