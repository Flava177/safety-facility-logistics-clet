package gh.edu.clet.sfl.safetysecurity.intrusion.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.safetysecurity.intrusion.application.service.IntrusionIngestionService;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.PanelHealthStatus;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.SignalType;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * SRS-SFL-S162-01: inbound intrusion-panel signals and panel/monitoring-service health. Every payload
 * passes {@code IntrusionIntegrationInbox} (HMAC + allowlist + schema + idempotency) BEFORE any
 * domain side effect - mirrors {@code AccessControlIntegrationController}, including HMAC replacing a
 * bearer token as the authentication mechanism (a panel or monitoring feed has no SFL user to present
 * a JWT as).
 */
@RestController
@RequestMapping("/api/v1/intrusion/integration")
@Tag(name = "S162 Intrusion Integration")
public class IntrusionIntegrationController {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final IntrusionIngestionService ingestion;
    private final IntrusionActorResolver actors;
    private final ObjectMapper json;

    public IntrusionIntegrationController(IntrusionIngestionService ingestion, IntrusionActorResolver actors,
            ObjectMapper json) {
        this.ingestion = ingestion;
        this.actors = actors;
        this.json = json;
    }

    @PostMapping("/panel-events/{source}")
    public ApiResponse<Object> panelEvent(@PathVariable String source,
            @RequestHeader("X-SFL-Integration-Signature") String signature,
            @RequestHeader("X-SFL-Integration-Timestamp") Instant signedAt, @RequestBody String raw,
            HttpServletRequest http) {
        Map<String, Object> payload = json.readValue(raw, MAP);
        String eventType = str(payload, "eventType");
        var actor = actors.resolveIntegrationActor(http, source);
        if ("PANEL_HEALTH".equalsIgnoreCase(eventType)) {
            return ApiResponse.ok(ingestion.reportHealth(new IntrusionIngestionService.ReportHealth(source,
                    str(payload, "externalEventId"), signedAt, signature, raw, payload, str(payload, "siteCode"),
                    str(payload, "panelId"), str(payload, "zoneCode"),
                    PanelHealthStatus.valueOf(str(payload, "status").toUpperCase(Locale.ROOT)),
                    Instant.parse(str(payload, "observedAt")), actor)));
        }
        return ApiResponse.ok(ingestion.ingest(new IntrusionIngestionService.IngestSignal(source,
                str(payload, "externalEventId"), signedAt, signature, raw, payload, str(payload, "siteCode"),
                str(payload, "panelId"), str(payload, "zoneCode"),
                SignalType.valueOf(str(payload, "signalType").toUpperCase(Locale.ROOT)),
                Instant.parse(str(payload, "occurredAt")), actor)));
    }

    private static String str(Map<String, Object> payload, String field) {
        Object value = payload == null ? null : payload.get(field);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return String.valueOf(value).strip();
    }
}
