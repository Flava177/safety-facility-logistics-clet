package gh.edu.clet.sfl.safetysecurity.cctv.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.safetysecurity.cctv.application.service.AnalyticsAlertService;
import gh.edu.clet.sfl.safetysecurity.cctv.application.service.CameraService;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.AlertSeverity;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.AlertType;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.CameraHealthStatus;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.RecordingStatus;
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
 * SRS-SFL-S161-01/04: inbound camera-health and video-analytics-alert events from the VMS. Every
 * payload passes {@code CctvIntegrationInbox} (HMAC + allowlist + schema + idempotency) BEFORE any
 * domain side effect - mirrors S160a's {@code AccessControlIntegrationController}.
 */
@RestController
@RequestMapping("/api/v1/cctv/integration")
@Tag(name = "S161 CCTV Integration")
public class CctvIntegrationController {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final CameraService cameras;
    private final AnalyticsAlertService alerts;
    private final CctvActorResolver actors;
    private final ObjectMapper json;

    public CctvIntegrationController(CameraService cameras, AnalyticsAlertService alerts, CctvActorResolver actors,
            ObjectMapper json) {
        this.cameras = cameras;
        this.alerts = alerts;
        this.actors = actors;
        this.json = json;
    }

    @PostMapping("/camera-health/{source}")
    public ApiResponse<Object> cameraHealth(@PathVariable String source,
            @RequestHeader("X-SFL-Integration-Signature") String signature,
            @RequestHeader("X-SFL-Integration-Timestamp") Instant signedAt, @RequestBody String raw,
            HttpServletRequest http) {
        Map<String, Object> payload = json.readValue(raw, MAP);
        var actor = actors.resolveIntegrationActor(http, source);
        return ApiResponse.ok(cameras.reportHealth(new CameraService.ReportHealth(source,
                str(payload, "externalEventId"), signedAt, signature, raw, payload, str(payload, "siteCode"),
                str(payload, "cameraId"), CameraHealthStatus.valueOf(str(payload, "status").toUpperCase(Locale.ROOT)),
                recordingStatusOf(payload), Instant.parse(str(payload, "observedAt")), actor)));
    }

    @PostMapping("/analytics-alerts/{source}")
    public ApiResponse<Object> analyticsAlert(@PathVariable String source,
            @RequestHeader("X-SFL-Integration-Signature") String signature,
            @RequestHeader("X-SFL-Integration-Timestamp") Instant signedAt, @RequestBody String raw,
            HttpServletRequest http) {
        Map<String, Object> payload = json.readValue(raw, MAP);
        var actor = actors.resolveIntegrationActor(http, source);
        return ApiResponse.ok(alerts.ingest(new AnalyticsAlertService.IngestAlert(source,
                str(payload, "externalEventId"), signedAt, signature, raw, payload, str(payload, "siteCode"),
                str(payload, "cameraId"), AlertType.valueOf(str(payload, "type").toUpperCase(Locale.ROOT)),
                severityOf(payload), Instant.parse(str(payload, "occurredAt")), actor)));
    }

    private static RecordingStatus recordingStatusOf(Map<String, Object> payload) {
        String value = nullable(payload, "recordingStatus");
        if (value == null) {
            return RecordingStatus.UNKNOWN;
        }
        try {
            return RecordingStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return RecordingStatus.UNKNOWN;
        }
    }

    private static AlertSeverity severityOf(Map<String, Object> payload) {
        String value = nullable(payload, "severity");
        return value == null ? AlertSeverity.MEDIUM : AlertSeverity.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private static String str(Map<String, Object> payload, String field) {
        Object value = payload == null ? null : payload.get(field);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return String.valueOf(value).strip();
    }

    private static String nullable(Map<String, Object> payload, String field) {
        Object value = payload == null ? null : payload.get(field);
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value).strip();
    }
}
