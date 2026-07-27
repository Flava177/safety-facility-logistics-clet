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

    @GetMapping
    public ApiResponse<List<CustodyHandover>> handovers(@RequestParam UUID dispatchId, HttpServletRequest h) {
        return ApiResponse.ok(service.handovers(dispatchId, actors.resolve(h)));
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
