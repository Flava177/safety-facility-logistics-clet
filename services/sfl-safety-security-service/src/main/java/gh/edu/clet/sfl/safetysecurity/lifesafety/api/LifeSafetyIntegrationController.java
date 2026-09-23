package gh.edu.clet.sfl.safetysecurity.lifesafety.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.safetysecurity.lifesafety.application.service.LifeSafetyEventIngestionService;
import gh.edu.clet.sfl.safetysecurity.lifesafety.application.service.LifeSafetyEventIngestionService.ObserveEvent;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.LifeSafetyEvent;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.LifeSafetyEventKind;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SRS-SFL-S162a-01: the vendor-facing ingress. Authenticated at the application layer (HMAC, source
 * allowlist, timestamp window) by {@code LifeSafetyIntegrationInbox}, not by a bearer token - see
 * {@code SafetySecurityConfiguration}'s {@code permitAll} for this path.
 */
@RestController
@RequestMapping("/api/v1/life-safety/integration")
public class LifeSafetyIntegrationController {

    private final LifeSafetyEventIngestionService ingestion;
    private final LifeSafetyActorResolver actors;

    public LifeSafetyIntegrationController(LifeSafetyEventIngestionService ingestion, LifeSafetyActorResolver actors) {
        this.ingestion = ingestion;
        this.actors = actors;
    }

    public record ObserveEventRequest(@NotBlank String siteCode, String deviceId, String zoneCode,
            @NotNull LifeSafetyEventKind kind, String externalEventId, @NotBlank String idempotencyKey,
            @NotNull Instant signedAt, @NotBlank String signature, @NotBlank String rawPayload) {
    }

    @PostMapping("/events")
    @Operation(summary = "Observe a life-safety event from the certified system's feed",
            description = "SRS-SFL-S162a-01: authenticated, idempotent, observe-only.")
    public ResponseEntity<ApiResponse<LifeSafetyEvent>> observe(@RequestHeader("X-SFL-Source") String source,
            @RequestBody ObserveEventRequest body, HttpServletRequest http) {
        var event = ingestion.observe(new ObserveEvent(source, body.idempotencyKey(), body.signedAt(),
                body.signature(), body.rawPayload(), body.siteCode(), body.deviceId(), body.zoneCode(), body.kind(),
                body.externalEventId(), actors.resolve(http)));
        return ResponseEntity.status(201).body(ApiResponse.ok(event));
    }
}
