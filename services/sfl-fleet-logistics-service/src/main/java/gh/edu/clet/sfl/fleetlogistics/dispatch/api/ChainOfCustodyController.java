package gh.edu.clet.sfl.fleetlogistics.dispatch.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchCustodyService;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchEvidenceSupport.EvidenceMeta;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CustodyHandover;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CustodyHop;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.SealState;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.FleetActorResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

/** S171-02 Chain of custody: record handovers (append-only) and inspect custody gaps. */
@RestController
@RequestMapping("/api/v1/dispatch/custody")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Chain of Custody")
public class ChainOfCustodyController {

    private final DispatchCustodyService service;
    private final FleetActorResolver actors;

    public ChainOfCustodyController(DispatchCustodyService service, FleetActorResolver actors) {
        this.service = service;
        this.actors = actors;
    }

    @PostMapping
    public ApiResponse<CustodyHandover> record(@Valid @RequestBody RecordHandoverRequest r, HttpServletRequest h) {
        EvidenceMeta evidence = r.evidenceStorageReference() == null || r.evidenceStorageReference().isBlank() ? null
                : new EvidenceMeta(r.evidenceFileName(), r.evidenceContentType(), r.evidenceStorageReference(),
                        r.evidenceSha256(), r.retentionClass(), null);
        return ApiResponse.ok(service.recordHandover(new DispatchCustodyService.RecordHandover(r.dispatchId(), r.hop(),
                r.transferringCustodian(), r.receivingCustodian(), r.occurredAt(), r.sealState(), r.verifiedCount(),
                r.notes(), evidence, actors.resolve(h), actors.resolveSourceChannel(h))));
    }

    /**
      * Custody handovers.
      *
      * <p>A {@code dispatchId} reads one consignment's chain, which is what the manifest screen
      * wants. A {@code siteCode} reads across consignments — closing gap 7, which made questions
      * like "every handover this custodian touched last week" unanswerable without knowing each
      * manifest in advance.
      */
    @GetMapping
    public ApiResponse<?> handovers(@RequestParam(required = false) UUID dispatchId,
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) CustodyHop hop,
            @RequestParam(required = false) String custodian,
            @RequestParam(required = false) SealState sealState,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String sort, HttpServletRequest h) {
        var actor = actors.resolve(h);
        if (siteCode != null && !siteCode.isBlank()) {
            return ApiResponse.ok(DispatchPageResponse.of(service.handovers(siteCode, dispatchId, hop, custodian,
                    sealState, from, to, DispatchPageResponse.paging(page, size, sort), actor)));
        }
        if (dispatchId == null) {
            throw new IllegalArgumentException("Either dispatchId or siteCode is required");
        }
        return ApiResponse.ok(service.handovers(dispatchId, actor));
    }

    @GetMapping("/{dispatchId}/gaps")
    public ApiResponse<DispatchCustodyService.CustodyGaps> gaps(@PathVariable UUID dispatchId, HttpServletRequest h) {
        return ApiResponse.ok(service.gaps(dispatchId, actors.resolve(h)));
    }

    public record RecordHandoverRequest(@NotNull UUID dispatchId, @NotNull CustodyHop hop,
            @NotBlank String transferringCustodian, @NotBlank String receivingCustodian, Instant occurredAt,
            @NotNull SealState sealState, Integer verifiedCount, String notes, String evidenceFileName,
            String evidenceContentType, String evidenceStorageReference, String evidenceSha256, String retentionClass) {}
}
