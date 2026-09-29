package gh.edu.clet.sfl.facilities.buildingsystems.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.buildingsystems.application.BuildingSystemsCommands;
import gh.edu.clet.sfl.facilities.buildingsystems.application.TelemetryIngestionService;
import gh.edu.clet.sfl.facilities.shared.application.vendor.SignedVendorMessage;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * BMS/IoT telemetry ingestion - SRS-SFL-S156-01.
 *
 * <p>The raw body is read as a {@code String} and signed over exactly as received - {@code @RequestBody
 * String} rather than a mapped type, because {@link gh.edu.clet.sfl.facilities.shared.application.vendor.VendorMessageVerifier}
 * must check the HMAC against the bytes that were sent, not a re-serialisation Jackson would produce.
 * Parsing happens here, in the API layer, only after that check has passed inside {@code accept}.
 *
 * <p>Headers: {@code X-SFL-Source} (the allowlisted source id), {@code X-SFL-Signature} (hex HMAC-SHA256),
 * {@code X-SFL-Signed-At} (ISO instant). Envelope fields inside the body: {@code messageType},
 * {@code idempotencyKey}, {@code siteCode}, and {@code format} - which adapter reads the reading array.
 *
 * <p>A quarantined reading answers {@code 202 Accepted} (see {@code FacilitiesApiExceptionHandler} - no
 * exception is thrown for quarantine, so this always returns 200 with the per-item outcome; quarantine is
 * acceptance for review, not refusal). A forged or malformed message never reaches this method's body: the
 * verifier throws before the request completes, mapped to {@code 401}.
 */
@RestController
@RequestMapping("/api/v1/facilities/building-systems")
@Tag(name = "S156 Building Management System / IoT",
        description = "Authenticated BMS/IoT telemetry ingestion, device inventory, threshold rules, health")
public class TelemetryIngestionController {

    private final TelemetryIngestionService ingestion;
    private final ObjectMapper json;

    public TelemetryIngestionController(TelemetryIngestionService ingestion, ObjectMapper json) {
        this.ingestion = ingestion;
        this.json = json;
    }

    @PostMapping(path = "/telemetry", consumes = "application/json")
    @Operation(summary = "Ingest authenticated BMS/IoT telemetry",
            description = "SRS-SFL-S156-01. Requires FACILITIES_BMS_TELEMETRY_INGEST, held only by "
                    + "integration principals. Verified by VendorMessageVerifier on BMS_TELEMETRY before "
                    + "anything in the body is trusted.")
    public ResponseEntity<ApiResponse<BuildingSystemsResponses.IngestionResponse>> telemetry(
            @RequestBody String rawBody, HttpServletRequest request, ActorContext actor, SourceChannel channel) {
        Map<String, Object> envelope = json.readValue(rawBody, new TypeReference<Map<String, Object>>() {
        });
        SignedVendorMessage message = new SignedVendorMessage(
                request.getHeader("X-SFL-Source"),
                text(envelope, "messageType"),
                text(envelope, "idempotencyKey"),
                text(envelope, "siteCode"),
                parseInstant(request.getHeader("X-SFL-Signed-At")),
                request.getHeader("X-SFL-Signature"),
                rawBody,
                envelope);
        BuildingSystemsCommands.IngestionResult result = ingestion.ingest(
                new BuildingSystemsCommands.IngestTelemetry(message, actor, channel));
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.ok(BuildingSystemsResponses.IngestionResponse.from(result)));
    }

    private static String text(Map<String, Object> envelope, String field) {
        Object value = envelope.get(field);
        return value == null ? null : String.valueOf(value);
    }

    private static Instant parseInstant(String header) {
        try {
            return header == null || header.isBlank() ? null : Instant.parse(header);
        } catch (java.time.format.DateTimeParseException malformed) {
            return null;
        }
    }
}
