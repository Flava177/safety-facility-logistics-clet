package gh.edu.clet.sfl.facilities.maintenance.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.maintenance.application.MaintenanceCommands;
import gh.edu.clet.sfl.facilities.maintenance.application.MaintenanceEvidenceService;
import gh.edu.clet.sfl.facilities.shared.api.FacilitiesActorResolver;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reading, exporting and holding evidence — SRS-SFL-S153-03.
 *
 * <p>Attachment lives on the work-order controller, because evidence is always attached to one and
 * the URL should say so. What is here is everything that happens to a piece of evidence <em>after</em>
 * it exists, which is not work-order-shaped: an auditor exporting it has no interest in the job it
 * came from, and a legal hold outlives the job entirely.
 */
@RestController
@RequestMapping("/api/v1/facilities/maintenance-evidence")
@Tag(name = "S153 Evidence", description = "Evidence metadata, approved export and legal hold")
public class MaintenanceEvidenceController {

    private final MaintenanceEvidenceService service;
    private final FacilitiesActorResolver actorResolver;

    public MaintenanceEvidenceController(MaintenanceEvidenceService service,
            FacilitiesActorResolver actorResolver) {
        this.service = service;
        this.actorResolver = actorResolver;
    }

    @GetMapping("/{evidenceId}")
    @Operation(summary = "Read one piece of evidence",
            description = "Metadata only. The file itself is in object storage, at fileReference.")
    public ApiResponse<MaintenanceResponses.EvidenceResponse> findById(@PathVariable UUID evidenceId,
            HttpServletRequest http) {
        return ApiResponse.ok(MaintenanceResponses.EvidenceResponse.from(
                service.findById(evidenceId, actor(http), channel(http))));
    }

    @PostMapping("/{evidenceId}/exports")
    @Operation(summary = "Approve an export of this evidence",
            description = "SRS-SFL-S153-03: export requires a role permission, a recorded reason and a "
                    + "named recipient, and the act itself is audited. This authorises and records the "
                    + "export and returns the reference to fetch; it does not move the file.")
    public ApiResponse<MaintenanceResponses.ExportGrantResponse> export(@PathVariable UUID evidenceId,
            @Valid @RequestBody MaintenanceRequests.ExportEvidence request, HttpServletRequest http) {
        return ApiResponse.ok(MaintenanceResponses.ExportGrantResponse.from(
                service.export(new MaintenanceCommands.ExportEvidence(evidenceId, request.reason(),
                        request.recipient(), actor(http), channel(http)))));
    }

    @PatchMapping("/{evidenceId}/legal-hold")
    @Operation(summary = "Place or lift a legal hold",
            description = "A hold suspends disposal without changing the retention class, so the "
                    + "original classification survives the hold being lifted. A reason is required "
                    + "either way.")
    public ApiResponse<MaintenanceResponses.EvidenceResponse> setLegalHold(@PathVariable UUID evidenceId,
            @Valid @RequestBody MaintenanceRequests.SetLegalHold request, HttpServletRequest http) {
        return ApiResponse.ok(MaintenanceResponses.EvidenceResponse.from(
                service.setLegalHold(new MaintenanceCommands.SetLegalHold(evidenceId, request.legalHold(),
                        request.reason(), actor(http), channel(http)))));
    }

    private ActorContext actor(HttpServletRequest http) {
        return actorResolver.resolve(http);
    }

    private SourceChannel channel(HttpServletRequest http) {
        return actorResolver.resolveSourceChannel(http);
    }
}
