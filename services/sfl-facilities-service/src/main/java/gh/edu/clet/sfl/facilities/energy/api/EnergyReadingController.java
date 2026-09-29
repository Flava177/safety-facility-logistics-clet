package gh.edu.clet.sfl.facilities.energy.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.energy.application.EnergyCommands;
import gh.edu.clet.sfl.facilities.energy.application.EnergyReadingService;
import gh.edu.clet.sfl.facilities.energy.application.ports.EnergyRepository;
import gh.edu.clet.sfl.facilities.energy.domain.ConsumptionReading;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyPeriod;
import gh.edu.clet.sfl.facilities.energy.domain.ReadingStatus;
import gh.edu.clet.sfl.facilities.energy.domain.Utility;
import gh.edu.clet.sfl.facilities.shared.api.IdempotencyKey;
import gh.edu.clet.sfl.facilities.shared.application.vendor.SignedVendorMessage;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Meter readings from the three paths, and the consumption record - SRS-SFL-S157-01.
 *
 * <h2>The AMI endpoint reads the raw body</h2>
 *
 * The signature is over the bytes as sent, so the body is taken as a string and parsed here only for the
 * schema checks; the verifier hashes the string. A body that is not JSON is still handed to the verifier
 * with an empty payload, so it is rejected and logged by the same path as every other failure rather than
 * bounced by a parser before anybody recorded it (NFR-SEC2).
 *
 * <h2>A held manual reading answers 202</h2>
 *
 * The service saves the held reading and returns it; this controller then answers
 * {@code ENERGY_READING_IMPLAUSIBLE} (mapped to 202 Accepted) with the reading id in the message. Thrown
 * here, after the service's transaction has committed, and never inside it - an exception inside the
 * transaction would roll the held reading back, which is exactly "silently" not accepting it.
 */
@RestController
@RequestMapping("/api/v1/facilities/energy")
@Tag(name = "S157 Energy readings", description = "AMI ingestion, manual reads with verification, consumption")
public class EnergyReadingController {

    static final String HEADER_SOURCE = "X-SFL-Source";
    static final String HEADER_SIGNATURE = "X-SFL-Signature";
    static final String HEADER_SIGNED_AT = "X-SFL-Signed-At";

    private final EnergyReadingService readings;
    private final ObjectMapper json;

    public EnergyReadingController(EnergyReadingService readings, ObjectMapper json) {
        this.readings = readings;
        this.json = json;
    }

    @PostMapping("/readings/ingest")
    @Operation(summary = "AMI gateway reading (signed)",
            description = "NFR-SEC2: authenticated (HMAC over the raw body), source-allowlisted, schema-validated "
                    + "before anything is posted. Requires FACILITIES_ENERGY_READING_INGEST. A duplicate "
                    + "idempotencyKey answers 200 with the original reading.")
    public ResponseEntity<ApiResponse<EnergyResponses.IngestResponse>> ingest(@RequestBody String body,
            @RequestHeader(value = HEADER_SOURCE, required = false) String sourceId,
            @RequestHeader(value = HEADER_SIGNATURE, required = false) String signature,
            @RequestHeader(value = HEADER_SIGNED_AT, required = false) String signedAt, ActorContext actor) {
        Map<String, Object> envelope = parse(body);
        Object payload = envelope.get("payload");
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = payload instanceof Map<?, ?> nested ? (Map<String, Object>) nested : envelope;
        SignedVendorMessage message = new SignedVendorMessage(sourceId, text(envelope.get("messageType")),
                text(envelope.get("idempotencyKey")), text(envelope.get("siteCode")), instant(signedAt), signature,
                body, fields);
        EnergyReadingService.IngestResult result = readings.ingest(message, actor);
        EnergyResponses.IngestResponse response = new EnergyResponses.IngestResponse(
                result.reading() == null ? null : EnergyResponses.ReadingResponse.from(result.reading()),
                result.duplicate());
        return result.duplicate() || result.reading() == null ? ResponseEntity.ok(ApiResponse.ok(response))
                : ResponseEntity.created(URI.create("/api/v1/facilities/energy/readings/" + result.reading().id()))
                        .body(ApiResponse.ok(response));
    }

    @PostMapping("/readings/manual")
    @Operation(summary = "Enter a manual register read",
            description = "Requires FACILITIES_ENERGY_READING_ENTER. Outside the plausibility band of the "
                    + "trailing average the reading is held for supervisor verification and the answer is 202 "
                    + "ENERGY_READING_IMPLAUSIBLE; it is not posted to the consumption record.")
    public ResponseEntity<ApiResponse<EnergyResponses.ReadingResponse>> enter(
            @Valid @RequestBody EnergyRequests.ManualReading request, ActorContext actor, SourceChannel channel,
            @IdempotencyKey String idempotencyKey) {
        ConsumptionReading reading = readings.enterManual(new EnergyCommands.EnterManualReading(request.meterId(),
                request.registerValue(), request.readAt(), request.note(), actor, channel, idempotencyKey, request));
        if (reading.status() == ReadingStatus.HELD) {
            throw new FacilitiesException(FacilitiesErrorCode.ENERGY_READING_IMPLAUSIBLE,
                    FacilitiesErrorCode.ENERGY_READING_IMPLAUSIBLE.defaultMessage() + " Reading " + reading.id()
                            + " is held. " + reading.holdReason());
        }
        return ResponseEntity.created(URI.create("/api/v1/facilities/energy/readings/" + reading.id()))
                .body(ApiResponse.ok(EnergyResponses.ReadingResponse.from(reading)));
    }

    @PatchMapping("/readings/{readingId}/verification")
    @Operation(summary = "Verify or reject a held reading",
            description = "Requires FACILITIES_ENERGY_READING_VERIFY, and a verifier other than the enterer "
                    + "(ENERGY_SELF_VERIFICATION). Records verified-by.")
    public ApiResponse<EnergyResponses.ReadingResponse> verify(@PathVariable UUID readingId,
            @Valid @RequestBody EnergyRequests.Verification request, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(EnergyResponses.ReadingResponse.from(readings.decide(new EnergyCommands.DecideHeldReading(
                readingId, request.approve(), request.consumption(), request.note(), actor, channel))));
    }

    @GetMapping("/readings")
    @Operation(summary = "List readings", description = "Newest first; status=HELD is the verification queue.")
    public ApiResponse<List<EnergyResponses.ReadingResponse>> list(@RequestParam(required = false) String siteCode,
            @RequestParam(required = false) UUID meterId, @RequestParam(required = false) ReadingStatus status,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "200") int limit, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(readings.readings(new EnergyRepository.ReadingQuery(siteCode, meterId, status, from, to,
                limit), actor, channel).stream().map(EnergyResponses.ReadingResponse::from).toList());
    }

    @GetMapping("/readings/{readingId}")
    @Operation(summary = "One reading, with its entered-by / verified-by trail")
    public ApiResponse<EnergyResponses.ReadingResponse> get(@PathVariable UUID readingId, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(EnergyResponses.ReadingResponse.from(readings.reading(readingId, actor, channel)));
    }

    @GetMapping("/consumption")
    @Operation(summary = "Daily or monthly consumption per utility and site (or building)",
            description = "From the consumption record: posted readings only; held readings are excluded.")
    public ApiResponse<List<EnergyResponses.ConsumptionResponse>> consumption(
            @RequestParam(required = false) String siteCode, @RequestParam(required = false) Utility utility,
            @RequestParam(defaultValue = "DAY") EnergyPeriod.PeriodType granularity,
            @RequestParam(defaultValue = "SITE") EnergyReadingService.Grouping groupBy,
            @RequestParam LocalDate from, @RequestParam LocalDate to, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(readings.consumption(siteCode, utility, granularity, groupBy, from, to, actor, channel)
                .stream().map(EnergyResponses.ConsumptionResponse::from).toList());
    }

    private Map<String, Object> parse(String body) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        try {
            Object parsed = json.readValue(body, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                return typed;
            }
            return Map.of();
        } catch (JacksonException unparseable) {
            return Map.of();
        }
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Instant instant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.strip());
        } catch (DateTimeParseException unreadable) {
            return null;
        }
    }
}
