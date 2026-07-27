package gh.edu.clet.sfl.emergencynotification.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.emergencynotification.application.service.ActivationService;
import gh.edu.clet.sfl.emergencynotification.domain.model.ChannelType;
import gh.edu.clet.sfl.emergencynotification.domain.model.NotificationActivation;
import gh.edu.clet.sfl.emergencynotification.domain.model.Priority;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * SRS/§0E break-glass activation: a declared-emergency broadcast that sends WITHOUT per-message approval
 * for an authorised {@code EMERGENCY_BREAK_GLASS_SEND} role and a break-glass-eligible template/scenario.
 * Closure is later blocked until after-the-fact approval is recorded.
 */
@RestController
@RequestMapping("/api/v1/emergency/activations/break-glass")
@Tag(name = "Break Glass")
public class BreakGlassController {

    private final ActivationService service;
    private final EmergencyActorResolver actors;

    public BreakGlassController(ActivationService service, EmergencyActorResolver actors) {
        this.service = service;
        this.actors = actors;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NotificationActivation>> breakGlass(@Valid @RequestBody BreakGlassRequest r,
            HttpServletRequest h) {
        var a = service.breakGlass(new ActivationService.CreateActivation(r.siteCode(), r.scenarioId(), r.templateId(),
                r.audienceGroupIds() == null ? List.of() : r.audienceGroupIds(),
                r.recipientZoneIds() == null ? List.of() : r.recipientZoneIds(),
                r.channels() == null ? List.of() : r.channels(), r.priority(), r.incidentReference(),
                actors.resolveIdempotencyKey(h), actors.resolve(h), actors.resolveSourceChannel(h)));
        return ResponseEntity.created(URI.create("/api/v1/emergency/activations/" + a.id())).body(ApiResponse.ok(a));
    }

    public record BreakGlassRequest(@NotBlank String siteCode, UUID scenarioId, @NotNull UUID templateId,
            List<UUID> audienceGroupIds, List<UUID> recipientZoneIds, @NotNull List<ChannelType> channels,
            Priority priority, String incidentReference) {}
}
