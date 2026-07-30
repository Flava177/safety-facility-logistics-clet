package gh.edu.clet.sfl.fleetlogistics.fleet.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.mapper.FleetIntegrationMapper;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.FleetIntegrationResponses.InboxMessageResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.FleetIntegrationResponses.IntegrationHealthResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.IntegrationCommands.ReceiveIntegrationMessage;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.IntegrationCommands.ReplayIntegrationMessage;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetIntegrationApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.IntegrationMessageStatus;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Secure integration intake and operations endpoints (SRS-SFL-S166-04). */
@RestController
@RequestMapping("/api/v1/fleet/integrations")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Integrations")
class FleetIntegrationController {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final FleetIntegrationApplicationService integrationService;
    private final FleetIntegrationMapper mapper;
    private final FleetActorResolver actorResolver;
    private final ObjectMapper objectMapper;

    FleetIntegrationController(FleetIntegrationApplicationService integrationService, FleetIntegrationMapper mapper,
            FleetActorResolver actorResolver, ObjectMapper objectMapper) {
        this.integrationService = integrationService;
        this.mapper = mapper;
        this.actorResolver = actorResolver;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{sourceSystem}/messages")
    ResponseEntity<ApiResponse<InboxMessageResponse>> receive(@PathVariable String sourceSystem,
            @RequestHeader("X-SFL-Integration-Signature") String signature,
            @RequestHeader("X-SFL-Integration-Timestamp") Instant signatureTimestamp,
            @RequestBody String rawPayload,
            HttpServletRequest httpRequest) throws JacksonException {
        JsonNode root = objectMapper.readTree(rawPayload);
        ActorContext actor = actorResolver.resolve(httpRequest);
        var message = integrationService.receive(new ReceiveIntegrationMessage(sourceSystem,
                actorResolver.resolveIdempotencyKey(httpRequest), text(root, "eventType"), text(root, "siteCode"),
                Instant.parse(text(root, "occurredAt")), signature, signatureTimestamp, rawPayload,
                objectMapper.convertValue(root.path("payload"), MAP_TYPE), actor,
                actorResolver.resolveSourceChannel(httpRequest)));
        return ResponseEntity.created(URI.create("/api/v1/fleet/integrations/messages/" + message.id()))
                .body(ApiResponse.ok(mapper.toResponse(message)));
    }

    /**
      * Searches the inbound integration inbox.
      *
      * <p>Closes gap 8. Replay takes a message identifier and the health projection carried only a
      * handful of recent messages, so dead-letter replay was a documented capability that could not
      * be reached from the dashboard at all.
      */
    @GetMapping("/messages")
    public ApiResponse<List<InboxMessageResponse>> messages(
            @RequestParam(required = false) String sourceSystem,
            @RequestParam(required = false) IntegrationMessageStatus status,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "50") int size, HttpServletRequest httpRequest) {
        return ApiResponse.ok(integrationService
                .searchMessages(sourceSystem, status, eventType, size, actorResolver.resolve(httpRequest)).stream()
                .map(mapper::toResponse)
                .toList());
    }

    @GetMapping("/health")
    ApiResponse<IntegrationHealthResponse> health(HttpServletRequest httpRequest) {
        return ApiResponse.ok(IntegrationHealthResponse.from(integrationService.health(actorResolver.resolve(httpRequest))));
    }

    @PostMapping("/messages/{messageId}/replay")
    ApiResponse<InboxMessageResponse> replay(@PathVariable UUID messageId, HttpServletRequest httpRequest) {
        return ApiResponse.ok(mapper.toResponse(integrationService.replay(new ReplayIntegrationMessage(messageId,
                actorResolver.resolve(httpRequest), actorResolver.resolveSourceChannel(httpRequest)))));
    }

    private static String text(JsonNode root, String field) {
        JsonNode value = root.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
}
