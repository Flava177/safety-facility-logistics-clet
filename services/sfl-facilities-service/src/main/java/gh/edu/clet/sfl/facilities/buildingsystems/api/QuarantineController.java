package gh.edu.clet.sfl.facilities.buildingsystems.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.buildingsystems.application.BuildingSystemsCommands;
import gh.edu.clet.sfl.facilities.buildingsystems.application.TelemetryIngestionService;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.QuarantineStatus;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quarantine review - SRS-SFL-S156-01, -04.
 *
 * <p>List, resolve by mapping/registering then releasing, or discard with a reason. Both actions need
 * {@code FACILITIES_BMS_QUARANTINE_RESOLVE}; a release is refused with a state-transition error unless the
 * device is registered and its location resolves (the mapping must already be fixed elsewhere - registering
 * the device or revising its location - before this endpoint can release the held reading).
 */
@RestController
@RequestMapping("/api/v1/facilities/building-systems/quarantine")
@Tag(name = "S156 Quarantine", description = "Review of telemetry held for an unresolvable location or an unregistered device")
public class QuarantineController {

    private final TelemetryIngestionService ingestion;

    public QuarantineController(TelemetryIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @GetMapping
    @Operation(summary = "List quarantined readings for a site")
    public ApiResponse<List<BuildingSystemsResponses.QuarantineResponse>> list(@RequestParam String siteCode,
            @RequestParam(required = false) QuarantineStatus status, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(ingestion.quarantine(siteCode, status, actor, channel).stream()
                .map(BuildingSystemsResponses.QuarantineResponse::from).toList());
    }

    @PatchMapping("/{quarantineId}/release")
    @Operation(summary = "Release a quarantined reading as fact",
            description = "Only once the device is registered and its location resolves through S152; "
                    + "re-checked here, not trusted from the caller.")
    public ApiResponse<BuildingSystemsResponses.QuarantineResponse> release(@PathVariable UUID quarantineId,
            @Valid @RequestBody BuildingSystemsRequests.ReleaseQuarantine request, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(BuildingSystemsResponses.QuarantineResponse.from(
                ingestion.release(new BuildingSystemsCommands.ReleaseQuarantine(quarantineId, request.note(), actor,
                        channel))));
    }

    @PatchMapping("/{quarantineId}/discard")
    @Operation(summary = "Discard a quarantined reading with a reason")
    public ApiResponse<BuildingSystemsResponses.QuarantineResponse> discard(@PathVariable UUID quarantineId,
            @Valid @RequestBody BuildingSystemsRequests.DiscardQuarantine request, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(BuildingSystemsResponses.QuarantineResponse.from(
                ingestion.discard(new BuildingSystemsCommands.DiscardQuarantine(quarantineId, request.reason(), actor,
                        channel))));
    }
}
