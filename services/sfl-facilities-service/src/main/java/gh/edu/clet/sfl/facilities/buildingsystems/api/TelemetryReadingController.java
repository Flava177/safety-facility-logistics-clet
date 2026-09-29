package gh.edu.clet.sfl.facilities.buildingsystems.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.buildingsystems.application.TelemetryIngestionService;
import gh.edu.clet.sfl.facilities.buildingsystems.application.ports.BuildingSystemsRepository;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Historical telemetry readings - SRS-SFL-S156-01: "retained per the configured retention policy". */
@RestController
@RequestMapping("/api/v1/facilities/building-systems/readings")
@Tag(name = "S156 Readings", description = "Historical telemetry readings held as fact")
public class TelemetryReadingController {

    private final TelemetryIngestionService ingestion;

    public TelemetryReadingController(TelemetryIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @GetMapping
    @Operation(summary = "Search readings", description = "SRS-SFL-S156-01. Newest first, capped at 1000 per page.")
    public ApiResponse<List<BuildingSystemsResponses.ReadingResponse>> search(@RequestParam String siteCode,
            @RequestParam(required = false) UUID deviceId, @RequestParam(required = false) String channel,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false, defaultValue = "200") int limit, ActorContext actor,
            SourceChannel sourceChannel) {
        return ApiResponse.ok(ingestion.readings(new BuildingSystemsRepository.ReadingQuery(siteCode, deviceId,
                channel, from, to, limit), actor, sourceChannel).stream()
                .map(BuildingSystemsResponses.ReadingResponse::from).toList());
    }
}
