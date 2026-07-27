package gh.edu.clet.sfl.emergencynotification.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.emergencynotification.application.service.DrillService;
import gh.edu.clet.sfl.emergencynotification.domain.model.DrillRun;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** SRS-SFL-S174-05: notification drills and performance reporting. */
@RestController
@RequestMapping("/api/v1/emergency/drills")
@Tag(name = "Drills")
public class DrillController {

    private final DrillService service;
    private final EmergencyActorResolver actors;

    public DrillController(DrillService service, EmergencyActorResolver actors) {
        this.service = service;
        this.actors = actors;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DrillRun>> start(@Valid @RequestBody StartRequest r, HttpServletRequest h) {
        var d = service.start(new DrillService.StartDrill(r.siteCode(), r.scenarioId(), r.targetRecipients(), r.notes(),
                actors.resolve(h), actors.resolveSourceChannel(h)));
        return ResponseEntity.created(URI.create("/api/v1/emergency/drills/" + d.id())).body(ApiResponse.ok(d));
    }

    @GetMapping
    public ApiResponse<List<DrillRun>> list(@RequestParam String siteCode, HttpServletRequest h) {
        return ApiResponse.ok(service.list(siteCode, actors.resolve(h)));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<DrillRun> complete(@PathVariable UUID id, @Valid @RequestBody CompleteRequest r,
            HttpServletRequest h) {
        return ApiResponse.ok(service.complete(id, r.reachedRecipients(), r.acknowledgedRecipients(),
                r.activationMillis(), r.notes(), actors.resolve(h), actors.resolveSourceChannel(h)));
    }

    public record StartRequest(@NotBlank String siteCode, UUID scenarioId, @PositiveOrZero int targetRecipients,
            String notes) {}

    public record CompleteRequest(@PositiveOrZero int reachedRecipients, @PositiveOrZero int acknowledgedRecipients,
            @PositiveOrZero long activationMillis, String notes) {}
}
