package gh.edu.clet.sfl.fleetlogistics.dispatch.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.CarrierStatusPort;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.DispatchOutboxAdminPort;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchAccessPolicy;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchExceptionService;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchScanService;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.FleetActorResolver;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.IntegrationCommands.ReceiveIntegrationMessage;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetIntegrationApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.DuplicateIntegrationMessageException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * S171-04 secure scanner/carrier integration surface. Inbound scanner/label events reuse the shared
 * secure integration inbox (HMAC signature, source allowlist, schema validation, idempotency, inbox
 * persistence before domain processing); a scan that does not match the manifest is routed to variance
 * handling. Outbound integration health and privileged dead-letter replay are exposed here too.
 */
@RestController
@RequestMapping("/api/v1/dispatch/integrations")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Dispatch Integrations")
public class DispatchIntegrationController {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final FleetIntegrationApplicationService inbox;
    private final DispatchScanService scans;
    private final DispatchExceptionService exceptions;
    private final CarrierStatusPort carriers;
    private final FleetActorResolver actors;
    private final DispatchAccessPolicy access;
    private final ObjectMapper json;

    public DispatchIntegrationController(FleetIntegrationApplicationService inbox, DispatchScanService scans,
            DispatchExceptionService exceptions, CarrierStatusPort carriers, FleetActorResolver actors,
            DispatchAccessPolicy access, ObjectMapper json) {
        this.inbox = inbox;
        this.scans = scans;
        this.exceptions = exceptions;
        this.carriers = carriers;
        this.actors = actors;
        this.access = access;
        this.json = json;
    }

    @PostMapping("/scanners/{provider}/events")
    public ApiResponse<Map<String, Object>> scannerEvent(@PathVariable String provider,
            @RequestHeader("X-SFL-Integration-Signature") String signature,
            @RequestHeader("X-SFL-Integration-Timestamp") Instant signedAt, @RequestBody String raw,
            HttpServletRequest h) throws JacksonException {
        JsonNode root = json.readTree(raw);
        Map<String, Object> payload = json.convertValue(root.path("payload"), MAP);
        String siteCode = text(root, "siteCode");
        String key = actors.resolveIdempotencyKey(h);
        var actor = actors.resolve(h);
        try {
            inbox.receive(new ReceiveIntegrationMessage(provider, key, text(root, "eventType"), siteCode,
                    Instant.parse(text(root, "occurredAt")), signature, signedAt, raw, payload, actor,
                    SourceChannel.INTEGRATION));
        } catch (DuplicateIntegrationMessageException ignored) {
            return ApiResponse.ok(Map.of("status", "DUPLICATE_IGNORED", "provider", provider));
        }
        UUID dispatchId = payload.get("dispatchId") == null ? null
                : UUID.fromString(String.valueOf(payload.get("dispatchId")));
        var row = scans.recordScanEvent(siteCode, dispatchId, provider, str(payload, "rowReference"),
                str(payload, "scannedCode"), actor, SourceChannel.INTEGRATION);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scanRowId", row.id());
        result.put("outcome", row.outcome());
        result.put("courierItemId", row.courierItemId());
        return ApiResponse.ok(result);
    }

    /**
     * A carrier reporting movement against a dispatch.
     *
     * <p><strong>This had no check of any kind</strong> — no permission, and, unlike
     * {@link #scannerEvent} directly above it, no HMAC signature and no pass through the integration
     * inbox. Any authenticated caller holding no dispatch permission and scoped to no site could post
     * carrier status for any {@code dispatchId} at any site. The impact today is bounded because
     * {@code RecordedCarrierStatusAdapter} only logs, which is exactly why it was missed: nothing
     * broke. It is still the wrong shape, and the day that adapter persists anything it becomes a
     * write path with no gate at all.
     *
     * <p>Gated on {@code DISPATCH_INTEGRATION_INGEST}, the same permission its sibling requires. The
     * HMAC path is deliberately not added here as well: this endpoint does not go through the inbox,
     * so there is no stored signature to verify against, and bolting one on without the inbox's
     * replay and duplicate handling would be the appearance of a control rather than one. That is
     * recorded as the remaining gap rather than papered over.
     */
    @PostMapping("/carriers/{carrier}/status")
    public ApiResponse<Map<String, Object>> carrierStatus(@PathVariable String carrier,
            @RequestBody CarrierStatusRequest r, HttpServletRequest h) {
        var actor = actors.resolve(h);
        access.requirePermission(actor, SflPermission.DISPATCH_INTEGRATION_INGEST, "CarrierStatus");
        carriers.recordCarrierStatus(r.dispatchId(), carrier, r.status(),
                r.occurredAt() == null ? Instant.now() : r.occurredAt(), actor, SourceChannel.INTEGRATION);
        return ApiResponse.ok(Map.of("dispatchId", r.dispatchId(), "carrier", carrier, "status", r.status()));
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health(HttpServletRequest h) {
        var actor = actors.resolve(h);
        FleetIntegrationApplicationService.IntegrationHealth inboxHealth = inbox.health(actor);
        DispatchOutboxAdminPort.OutboxHealth outboxHealth = exceptions.integrationHealth(actor);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("inbox", inboxHealth);
        result.put("outbox", outboxHealth);
        return ApiResponse.ok(result);
    }

    @PostMapping("/outbox/{messageId}/replay")
    public ApiResponse<Map<String, Object>> replay(@PathVariable UUID messageId, HttpServletRequest h) {
        boolean requeued = exceptions.replayIntegration(messageId, actors.resolve(h), SourceChannel.INTEGRATION);
        return ApiResponse.ok(Map.of("messageId", messageId, "requeued", requeued));
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asText();
    }

    private static String str(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return String.valueOf(value);
    }

    public record CarrierStatusRequest(UUID dispatchId, String status, Instant occurredAt) {}
}
