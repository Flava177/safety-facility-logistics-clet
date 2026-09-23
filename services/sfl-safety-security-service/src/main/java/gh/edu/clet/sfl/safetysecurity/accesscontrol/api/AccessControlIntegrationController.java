package gh.edu.clet.sfl.safetysecurity.accesscontrol.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.application.service.AccessEventIngestionService;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.application.service.AccessProvisioningService;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessDirection;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessEventKind;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.ProvisioningBasis;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.ReaderHealthStatus;
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
 * SRS-SFL-S160a-01/02: inbound access-control vendor events/reader health, and HRMS/IAM
 * joiner-mover-leaver events. Every payload passes {@code AccessControlIntegrationInbox} (HMAC +
 * allowlist + schema + idempotency) BEFORE any domain side effect - mirrors S174's
 * {@code ProviderCallbackController}, including HMAC replacing a bearer token as the authentication
 * mechanism (a door controller or an HRMS batch job has no SFL user to present a JWT as).
 */
@RestController
@RequestMapping("/api/v1/access-control/integration")
@Tag(name = "S160a Access Control Integration")
public class AccessControlIntegrationController {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final AccessEventIngestionService ingestion;
    private final AccessProvisioningService provisioning;
    private final AccessControlActorResolver actors;
    private final ObjectMapper json;

    public AccessControlIntegrationController(AccessEventIngestionService ingestion,
            AccessProvisioningService provisioning, AccessControlActorResolver actors, ObjectMapper json) {
        this.ingestion = ingestion;
        this.provisioning = provisioning;
        this.actors = actors;
        this.json = json;
    }

    @PostMapping("/vendor-events/{source}")
    public ApiResponse<Object> vendorEvent(@PathVariable String source,
            @RequestHeader("X-SFL-Integration-Signature") String signature,
            @RequestHeader("X-SFL-Integration-Timestamp") Instant signedAt, @RequestBody String raw,
            HttpServletRequest http) {
        Map<String, Object> payload = json.readValue(raw, MAP);
        String eventType = str(payload, "eventType");
        var actor = actors.resolveIntegrationActor(http, source);
        if ("READER_HEALTH".equalsIgnoreCase(eventType)) {
            return ApiResponse.ok(ingestion.reportHealth(new AccessEventIngestionService.ReportHealth(source,
                    str(payload, "externalEventId"), signedAt, signature, raw, payload, str(payload, "siteCode"),
                    str(payload, "readerId"), str(payload, "zoneCode"),
                    ReaderHealthStatus.valueOf(str(payload, "status").toUpperCase(Locale.ROOT)),
                    Instant.parse(str(payload, "observedAt")), actor)));
        }
        return ApiResponse.ok(ingestion.ingest(new AccessEventIngestionService.IngestEvent(source,
                str(payload, "externalEventId"), signedAt, signature, raw, payload, str(payload, "siteCode"),
                str(payload, "readerId"), nullable(payload, "doorId"), str(payload, "zoneCode"),
                nullable(payload, "personRef"), AccessEventKind.valueOf(str(payload, "kind").toUpperCase(Locale.ROOT)),
                directionOf(payload), Instant.parse(str(payload, "occurredAt")), actor)));
    }

    @PostMapping("/hrms-events/{source}")
    public ApiResponse<Object> hrmsEvent(@PathVariable String source,
            @RequestHeader("X-SFL-Integration-Signature") String signature,
            @RequestHeader("X-SFL-Integration-Timestamp") Instant signedAt, @RequestBody String raw,
            HttpServletRequest http) {
        Map<String, Object> payload = json.readValue(raw, MAP);
        var actor = actors.resolveIntegrationActor(http, source);
        return ApiResponse.ok(provisioning.applyJoinerMoverLeaver(new AccessProvisioningService.JoinerMoverLeaverEvent(
                source, str(payload, "externalEventId"), signedAt, signature, raw, payload, str(payload, "siteCode"),
                str(payload, "personRef"), nullable(payload, "zoneCode"),
                ProvisioningBasis.valueOf(str(payload, "basis").toUpperCase(Locale.ROOT)), actor)));
    }

    private static AccessDirection directionOf(Map<String, Object> payload) {
        String direction = nullable(payload, "direction");
        if (direction == null) {
            return AccessDirection.UNKNOWN;
        }
        try {
            return AccessDirection.valueOf(direction.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return AccessDirection.UNKNOWN;
        }
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
