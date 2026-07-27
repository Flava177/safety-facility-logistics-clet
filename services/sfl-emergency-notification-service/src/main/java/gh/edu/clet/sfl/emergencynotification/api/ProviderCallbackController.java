package gh.edu.clet.sfl.emergencynotification.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.emergencynotification.application.service.ProviderCallbackService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * SRS-SFL-S174-04: inbound provider delivery-status and acknowledgement callbacks. Every payload passes
 * the secure inbox (HMAC + allowlist + schema + idempotency) BEFORE any domain side effect.
 */
@RestController
@RequestMapping("/api/v1/emergency/provider-callbacks")
@Tag(name = "Delivery and Acknowledgements")
public class ProviderCallbackController {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final ProviderCallbackService service;
    private final EmergencyActorResolver actors;
    private final ObjectMapper json;

    public ProviderCallbackController(ProviderCallbackService service, EmergencyActorResolver actors,
            ObjectMapper json) {
        this.service = service;
        this.actors = actors;
        this.json = json;
    }

    @PostMapping("/{provider}/delivery-status")
    public ApiResponse<Object> deliveryStatus(@PathVariable String provider,
            @RequestHeader("X-SFL-Integration-Signature") String signature,
            @RequestHeader("X-SFL-Integration-Timestamp") Instant signedAt, @RequestBody String raw,
            HttpServletRequest h) {
        Map<String, Object> payload = json.readValue(raw, MAP);
        return ApiResponse.ok(service.deliveryStatus(new ProviderCallbackService.ProviderCallback(provider, signature,
                signedAt, raw, payload, actors.resolve(h))));
    }

    @PostMapping("/{provider}/acknowledgements")
    public ApiResponse<Object> acknowledgement(@PathVariable String provider,
            @RequestHeader("X-SFL-Integration-Signature") String signature,
            @RequestHeader("X-SFL-Integration-Timestamp") Instant signedAt, @RequestBody String raw,
            HttpServletRequest h) {
        Map<String, Object> payload = json.readValue(raw, MAP);
        return ApiResponse.ok(service.acknowledgement(new ProviderCallbackService.ProviderCallback(provider, signature,
                signedAt, raw, payload, actors.resolve(h))));
    }
}
