package gh.edu.clet.sfl.facilities.spaceplanning.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.spaceplanning.application.SpacePlanningCommands;
import gh.edu.clet.sfl.facilities.spaceplanning.application.UtilisationReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Utilisation reconciliation against S159 - SRS-SFL-S158-03. Read-only from S159's side. */
@RestController
@RequestMapping("/api/v1/facilities/space-planning/utilisation")
@Tag(name = "S158 Space Planning", description = "Utilisation snapshots and planning signals")
public class UtilisationController {

    private final UtilisationReconciliationService service;

    public UtilisationController(UtilisationReconciliationService service) {
        this.service = service;
    }

    @PostMapping("/reconcile")
    @Operation(summary = "Run reconciliation for a site now",
            description = "Normally scheduled; exposed for an on-demand run or a missed-schedule catch-up. "
                    + "Evaluates the latest complete period unless periodEnd names an earlier boundary.")
    public ApiResponse<SpacePlanningResponses.ReconciliationRunResponse> reconcile(
            @Valid @RequestBody SpacePlanningRequests.RunReconciliation request, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(SpacePlanningResponses.ReconciliationRunResponse.from(
                service.reconcile(new SpacePlanningCommands.RunReconciliation(request.siteCode(),
                        request.periodEnd(), actor, channel))));
    }

    @GetMapping("/signals")
    @Operation(summary = "Planning signals for a site",
            description = "Under-utilisation and planned-versus-actual gap. activeOnly defaults to true.")
    public ApiResponse<List<SpacePlanningResponses.SignalResponse>> signals(@RequestParam String siteCode,
            @RequestParam(defaultValue = "true") boolean activeOnly, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(service.signals(siteCode, activeOnly, actor, channel).stream()
                .map(SpacePlanningResponses.SignalResponse::from).toList());
    }

    @GetMapping("/snapshots")
    @Operation(summary = "The latest utilisation snapshot for every room at a site")
    public ApiResponse<List<SpacePlanningResponses.SnapshotResponse>> snapshots(@RequestParam String siteCode,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(service.latestSnapshots(siteCode, actor, channel).stream()
                .map(SpacePlanningResponses.SnapshotResponse::from).toList());
    }
}
