package gh.edu.clet.sfl.emergencynotification.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.emergencynotification.application.port.InboxAdminPort;
import gh.edu.clet.sfl.emergencynotification.application.port.OutboxAdminPort;
import gh.edu.clet.sfl.emergencynotification.application.service.EmergencyIntegrationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

/** SRS-SFL-S174-04: integration health and privileged dead-letter replay. */
@RestController
@RequestMapping("/api/v1/emergency/integrations")
@Tag(name = "Integrations")
public class EmergencyIntegrationController {

    private final EmergencyIntegrationService service;
    private final EmergencyActorResolver actors;

    public EmergencyIntegrationController(EmergencyIntegrationService service, EmergencyActorResolver actors) {
        this.service = service;
        this.actors = actors;
    }

    /**
     * The inbound provider feed — processed, rejected and dead-lettered counts plus recent envelopes.
     *
     * <p>Closes gap 3, the most consequential gap on this service: this feed is the only thing that
     * ever writes {@code delivered}, {@code failed} and {@code acknowledged}, and none of it was
     * readable. A screen showing 480 sent and 0 delivered could not tell "no provider configured"
     * from "every callback rejected for a bad signature".
     */
    @GetMapping("/inbox")
    public ApiResponse<InboxAdminPort.InboxHealth> inbox(
            @RequestParam(defaultValue = "20") int recentLimit, HttpServletRequest h) {
        return ApiResponse.ok(service.inboxHealth(recentLimit, actors.resolve(h)));
    }

    @GetMapping("/health")
    public ApiResponse<OutboxAdminPort.OutboxHealth> health(HttpServletRequest h) {
        return ApiResponse.ok(service.health(actors.resolve(h)));
    }

    @PostMapping("/outbox/{messageId}/replay")
    public ApiResponse<Map<String, Object>> replay(@PathVariable UUID messageId, HttpServletRequest h) {
        boolean requeued = service.replay(messageId, actors.resolve(h), actors.resolveSourceChannel(h));
        return ApiResponse.ok(Map.of("messageId", messageId, "requeued", requeued));
    }
}
