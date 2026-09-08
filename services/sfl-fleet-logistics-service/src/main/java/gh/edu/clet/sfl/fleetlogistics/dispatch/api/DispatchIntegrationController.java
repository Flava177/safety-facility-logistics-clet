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
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.MalformedRequestValueException;
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

    @io.swagger.v3.oas.annotations.Operation(summary = "Ingests a signed scanner/label event through the secure integration inbox")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "The payload failed schema validation, or a value could not be parsed")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "HMAC signature verification failed")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "The source system is not allowlisted for this site")
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
                    parseInstant("occurredAt", text(root, "occurredAt")), signature, signedAt, raw, payload, actor,
                    SourceChannel.INTEGRATION));
        } catch (DuplicateIntegrationMessageException ignored) {
            return ApiResponse.ok(Map.of("status", "DUPLICATE_IGNORED", "provider", provider));
        }
        UUID dispatchId = payload.get("dispatchId") == null ? null
                : parseUuid("dispatchId", String.valueOf(payload.get("dispatchId")));
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
     * <p>This originally had no check beyond the {@code DISPATCH_INTEGRATION_INGEST} permission -
     * unlike {@link #scannerEvent} directly above it, no HMAC signature and no site scope on the
     * permission check, so any authenticated caller holding that one permission could post carrier
     * status for any {@code dispatchId} at any site. The impact was bounded only because
     * {@code RecordedCarrierStatusAdapter} still only logs; the day it persists something, an
     * unguarded write path was waiting for it. Rather than leave that for whoever wires up
     * persistence to remember, the same signature verification {@link #scannerEvent} uses is applied
     * here too, even though there is still no inbox row for it to be checked against - {@link
     * FleetIntegrationApplicationService#verifySignature} checks only the allowlist and the HMAC, not
     * schema, idempotency or inbox persistence, so it does not require inventing an inbox entry for a
     * write that does not happen yet.
     *
     * <p>{@code siteCode} moved from implicit (absent) to a required field for the same reason
     * {@link #scannerEvent} takes one: it is what selects which site's shared secret to verify
     * against, and it lets the permission check be site-scoped rather than global.
     */
    @io.swagger.v3.oas.annotations.Operation(summary = "Records a carrier's reported status for a dispatch, signature-verified like scannerEvent")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "A value in the payload could not be parsed")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "HMAC signature verification failed")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Actor lacks DISPATCH_INTEGRATION_INGEST for the site, or the source is not allowlisted")
    @PostMapping("/carriers/{carrier}/status")
    public ApiResponse<Map<String, Object>> carrierStatus(@PathVariable String carrier,
            @RequestHeader("X-SFL-Integration-Signature") String signature,
            @RequestHeader("X-SFL-Integration-Timestamp") Instant signedAt, @RequestBody String raw,
            HttpServletRequest h) throws JacksonException {
        JsonNode root = json.readTree(raw);
        String siteCode = text(root, "siteCode");
        var actor = actors.resolve(h);
        access.require(actor, SflPermission.DISPATCH_INTEGRATION_INGEST, siteCode, "CarrierStatus", null);
        inbox.verifySignature(carrier, siteCode, signedAt, raw, signature);
        UUID dispatchId = parseUuid("dispatchId", text(root, "dispatchId"));
        String status = text(root, "status");
        String occurredAtText = text(root, "occurredAt");
        Instant occurredAt = occurredAtText.isBlank() ? Instant.now() : parseInstant("occurredAt", occurredAtText);
        carriers.recordCarrierStatus(dispatchId, carrier, status, occurredAt, actor, SourceChannel.INTEGRATION);
        return ApiResponse.ok(Map.of("dispatchId", dispatchId, "carrier", carrier, "status", status));
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Reports inbound-inbox and outbound-outbox integration health")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Actor lacks the required integration health read permission")
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

    @io.swagger.v3.oas.annotations.Operation(summary = "Requeues a dead-lettered outbound integration message for another delivery attempt")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Actor lacks the required integration replay permission")
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

    /**
     * {@code UUID.fromString} throws a bare {@code IllegalArgumentException}, which the blanket
     * handler in {@code FleetApiExceptionHandler} would map to 400 anyway - but so would an
     * unrelated {@code IllegalArgumentException} thrown by code that has nothing to do with request
     * parsing. Rethrown here as the dedicated error so this specific, known-risky call site cannot be
     * silently reclassified alongside that bug case.
     */
    private static UUID parseUuid(String field, String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw MalformedRequestValueException.of(field, value);
        }
    }

    /** As {@link #parseUuid}, for {@code Instant.parse}'s {@code DateTimeParseException}. */
    private static Instant parseInstant(String field, String value) {
        try {
            return Instant.parse(value);
        } catch (java.time.format.DateTimeParseException exception) {
            throw MalformedRequestValueException.of(field, value);
        }
    }
}
