package gh.edu.clet.sfl.facilities.construction.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.construction.application.ConstructionCommands;
import gh.edu.clet.sfl.facilities.construction.application.VariationService;
import gh.edu.clet.sfl.facilities.construction.domain.VariationOrder;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Variation orders and budget control - SRS-SFL-S176-03. */
@RestController
@RequestMapping("/api/v1/facilities/construction/projects/{projectId}/variations")
@Tag(name = "S176 Variations", description = "Variation orders and budget control")
public class VariationController {

    private final VariationService service;

    public VariationController(VariationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Submit a variation order",
            description = "SRS-SFL-S176-03. A variation submitted while cumulative approved variations "
                    + "are already above the configured threshold, or while another is awaiting "
                    + "escalated approval, is held for escalation from the moment it is submitted.")
    public ResponseEntity<ApiResponse<ConstructionResponses.VariationResponse>> submit(@PathVariable UUID projectId,
            @Valid @RequestBody ConstructionRequests.SubmitVariation request, ActorContext actor,
            SourceChannel channel) {
        VariationOrder variation = service.submit(new ConstructionCommands.SubmitVariation(projectId,
                request.changeDescription(), request.costDelta(), request.currency(), request.justification(), actor,
                channel));
        return ResponseEntity.created(URI.create("/api/v1/facilities/construction/projects/" + projectId
                        + "/variations/" + variation.id()))
                .body(ApiResponse.ok(ConstructionResponses.VariationResponse.from(variation)));
    }

    @PatchMapping("/{variationId}/decision")
    @Operation(summary = "Approve or reject a variation",
            description = "SRS-SFL-S176-03 AC: an approval of a variation that needs escalation is "
                    + "refused with VARIATION_ESCALATION_REQUIRED and the variation is held, not "
                    + "silently approved at the ordinary level. The submitter cannot decide their own.")
    public ApiResponse<ConstructionResponses.VariationResponse> decide(@PathVariable UUID projectId,
            @PathVariable UUID variationId, @Valid @RequestBody ConstructionRequests.DecideVariation request,
            ActorContext actor, SourceChannel channel) {
        return respond(service.decide(new ConstructionCommands.DecideVariation(variationId, request.approve(),
                request.note(), actor, channel)));
    }

    @PatchMapping("/{variationId}/escalated-approval")
    @Operation(summary = "Escalated sign-off for a held variation",
            description = "Requires FACILITIES_VARIATION_ESCALATED_APPROVE. Refused if an earlier "
                    + "held variation on the same project has not yet been decided.")
    public ApiResponse<ConstructionResponses.VariationResponse> approveEscalated(@PathVariable UUID projectId,
            @PathVariable UUID variationId, @Valid @RequestBody ConstructionRequests.EscalatedApproval request,
            ActorContext actor, SourceChannel channel) {
        return respond(service.approveEscalated(new ConstructionCommands.EscalatedApproval(variationId,
                request.note(), actor, channel)));
    }

    @GetMapping
    @Operation(summary = "Every variation on the project")
    public ApiResponse<List<ConstructionResponses.VariationResponse>> list(@PathVariable UUID projectId,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(service.variations(projectId, actor, channel).stream()
                .map(ConstructionResponses.VariationResponse::from).toList());
    }

    private ApiResponse<ConstructionResponses.VariationResponse> respond(VariationOrder variation) {
        return ApiResponse.ok(ConstructionResponses.VariationResponse.from(variation));
    }
}
