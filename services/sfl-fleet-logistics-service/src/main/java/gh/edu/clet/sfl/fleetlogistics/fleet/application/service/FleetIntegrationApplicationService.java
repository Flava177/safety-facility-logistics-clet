package gh.edu.clet.sfl.fleetlogistics.fleet.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.IntegrationCommands.ReceiveIntegrationMessage;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.IntegrationCommands.ReplayIntegrationMessage;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IntegrationEventPublisher;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IntegrationInboxRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.RuntimeConfigurationPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleLocationRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.event.FleetEventType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.DuplicateIntegrationMessageException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.IntegrationConfigurationNotFoundException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.InvalidSignatureException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RecordNotFoundException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.SchemaValidationFailedException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.SourceNotAllowedException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.IntegrationInboxMessage;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.IntegrationMessageStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleLocationSnapshot;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Secure integration intake, health and replay use cases (SRS-SFL-S166-04). */
@Service
public class FleetIntegrationApplicationService {

    private static final String RESOURCE_TYPE = "IntegrationInboxMessage";
    private static final String LOCATION_EVENT = "sfl.ftlmp.vehicle-location-received.v1";

    private final IntegrationInboxRepository inbox;
    private final VehicleLocationRepository locations;
    private final RuntimeConfigurationPort configuration;
    private final FleetAccessPolicy accessPolicy;
    private final AuditPort auditPort;
    private final IntegrationEventPublisher eventPublisher;
    private final Clock clock;

    public FleetIntegrationApplicationService(IntegrationInboxRepository inbox, VehicleLocationRepository locations,
            RuntimeConfigurationPort configuration, FleetAccessPolicy accessPolicy, AuditPort auditPort,
            IntegrationEventPublisher eventPublisher, Clock clock) {
        this.inbox = inbox;
        this.locations = locations;
        this.configuration = configuration;
        this.accessPolicy = accessPolicy;
        this.auditPort = auditPort;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public IntegrationInboxMessage receive(ReceiveIntegrationMessage command) {
        SiteCode site = SiteCode.of(command.siteCode());
        accessPolicy.require(command.actor(), SflPermission.FLEET_INTEGRATION_INGEST, site, RESOURCE_TYPE, null);
        String source = normaliseSource(command.sourceSystem());
        requireAllowlisted(source, site.value());
        requireValidSignature(source, site.value(), command.signatureTimestamp(), command.rawPayload(),
                command.signature());
        validateSchema(command);
        inbox.findBySourceAndIdempotencyKey(source, command.idempotencyKey()).ifPresent(existing -> {
            throw new DuplicateIntegrationMessageException(Map.of(
                    "sourceSystem", source,
                    "idempotencyKey", command.idempotencyKey(),
                    "messageId", existing.id().toString()));
        });

        IntegrationInboxMessage accepted = IntegrationInboxMessage.accept(UUID.randomUUID(), source,
                command.idempotencyKey(), command.eventType(), site, command.actor().correlationId(),
                command.occurredAt(), sha256(command.rawPayload()), command.rawPayload(), clock.instant());
        IntegrationInboxMessage saved = inbox.save(accepted);
        auditPort.record(command.actor(), SourceChannel.INTEGRATION, site, AuditAction.INTEGRATION_ACCEPTED,
                RESOURCE_TYPE, saved.id().toString(), null, saved.auditImage());
        return process(saved, command.payload(), command.actor());
    }

    @Transactional(readOnly = true)
    public IntegrationHealth health(ActorContext actor) {
        accessPolicy.requirePermission(actor, SflPermission.FLEET_INTEGRATION_HEALTH_READ, "IntegrationHealth");
        return new IntegrationHealth(clock.instant(), inbox.countByStatus(IntegrationMessageStatus.PROCESSED),
                inbox.countByStatus(IntegrationMessageStatus.REJECTED),
                inbox.countByStatus(IntegrationMessageStatus.DEAD_LETTER),
                inbox.findRecent(20).stream().map(IntegrationMessageSummary::from).toList());
    }

    @Transactional
    /**
     * Searches the inbound inbox.
     *
     * <p>Closes gap 8. Replay needs a message identifier, and the health projection only ever
     * carried a handful of recent messages — so dead-letter replay was a documented capability that
     * could not be reached from the dashboard at all.
     */
    public List<IntegrationInboxMessage> searchMessages(String sourceSystem, IntegrationMessageStatus status,
            String eventType, int limit, ActorContext actor) {
        accessPolicy.requirePermission(actor, SflPermission.FLEET_INTEGRATION_REPLAY, "IntegrationInbox");
        return inbox.search(sourceSystem, status, eventType, limit);
    }

    public IntegrationInboxMessage replay(ReplayIntegrationMessage command) {
        IntegrationInboxMessage message = inbox.findById(command.messageId())
                .orElseThrow(() -> RecordNotFoundException.of(RESOURCE_TYPE, command.messageId()));
        accessPolicy.require(command.actor(), SflPermission.FLEET_INTEGRATION_REPLAY, message.siteCode(),
                RESOURCE_TYPE, message.id().toString());
        auditPort.record(command.actor(), SourceChannel.API, message.siteCode(), AuditAction.INTEGRATION_REPLAYED,
                RESOURCE_TYPE, message.id().toString(), message.auditImage(), message.auditImage());
        if (message.status() == IntegrationMessageStatus.PROCESSED) {
            return message;
        }
        IntegrationInboxMessage replayed = message.processed(clock.instant());
        return inbox.save(replayed);
    }

    private IntegrationInboxMessage process(IntegrationInboxMessage message, Map<String, Object> payload,
            ActorContext actor) {
        try {
            if (LOCATION_EVENT.equals(message.eventType())) {
                VehicleLocationSnapshot snapshot = toLocation(message, payload);
                locations.save(snapshot);
                eventPublisher.publish(FleetEventType.VEHICLE_LOCATION_RECEIVED, "Vehicle",
                        snapshot.vehicleId().toString(), snapshot.siteCode(), actor, message.id().toString(),
                        snapshot.auditImage());
            }
            return inbox.save(message.processed(clock.instant()));
        } catch (RuntimeException exception) {
            IntegrationInboxMessage deadLetter = inbox.save(message.deadLetter(exception.getMessage(), clock.instant()));
            auditPort.record(actor, SourceChannel.INTEGRATION, message.siteCode(), AuditAction.INTEGRATION_REJECTED,
                    RESOURCE_TYPE, message.id().toString(), message.auditImage(), deadLetter.auditImage());
            return deadLetter;
        }
    }

    private VehicleLocationSnapshot toLocation(IntegrationInboxMessage message, Map<String, Object> payload) {
        return new VehicleLocationSnapshot(UUID.randomUUID(),
                UUID.fromString(required(payload, "vehicleId")), message.siteCode(),
                new BigDecimal(required(payload, "latitude")), new BigDecimal(required(payload, "longitude")),
                optionalLong(payload.get("odometerValue")), message.occurredAt(), message.sourceSystem(),
                message.id(), message.correlationId());
    }

    private void requireAllowlisted(String source, String siteCode) {
        boolean enabled = configuration.value("fleet.integration." + source + ".enabled", siteCode)
                .map(Boolean::parseBoolean)
                .orElse(false);
        if (!enabled) {
            throw new SourceNotAllowedException(Map.of("sourceSystem", source, "siteCode", siteCode));
        }
    }

    private void requireValidSignature(String source, String siteCode, Instant timestamp, String rawPayload,
            String signature) {
        String secret = configuration.value("fleet.integration." + source + ".secret", siteCode)
                .orElseThrow(() -> new IntegrationConfigurationNotFoundException(Map.of(
                        "sourceSystem", source, "siteCode", siteCode, "configurationKey",
                        "fleet.integration." + source + ".secret")));
        if (timestamp == null || Duration.between(timestamp, clock.instant()).abs()
                .compareTo(configuration.integrationSignatureWindow()) > 0) {
            throw new InvalidSignatureException(Map.of("sourceSystem", source, "reason", "timestamp outside window"));
        }
        String expected = hmac(secret, timestamp + "." + rawPayload);
        if (signature == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.strip().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidSignatureException(Map.of("sourceSystem", source, "reason", "HMAC mismatch"));
        }
    }

    private static void validateSchema(ReceiveIntegrationMessage command) {
        Map<String, Object> payload = command.payload();
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.eventType() == null || command.eventType().isBlank()
                || command.siteCode() == null || command.siteCode().isBlank()
                || command.occurredAt() == null || payload == null || payload.isEmpty()) {
            throw new SchemaValidationFailedException(Map.of("sourceSystem", command.sourceSystem()));
        }
        if (LOCATION_EVENT.equals(command.eventType())) {
            required(payload, "vehicleId");
            required(payload, "latitude");
            required(payload, "longitude");
        }
    }

    private static String required(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new SchemaValidationFailedException(Map.of("field", field));
        }
        return String.valueOf(value).strip();
    }

    private static Long optionalLong(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : Long.valueOf(String.valueOf(value));
    }

    private static String normaliseSource(String source) {
        if (source == null || source.isBlank()) {
            throw new SourceNotAllowedException(Map.of("sourceSystem", ""));
        }
        return source.strip().toUpperCase(Locale.ROOT);
    }

    private static String sha256(String rawPayload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static String hmac(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }

    public record IntegrationHealth(Instant checkedAt, long processedMessages, long rejectedMessages,
            long deadLetterMessages, List<IntegrationMessageSummary> recentMessages) {
    }

    public record IntegrationMessageSummary(UUID id, String sourceSystem, String idempotencyKey, String eventType,
            String siteCode, IntegrationMessageStatus status, int attempts, Instant receivedAt, Instant processedAt) {

        static IntegrationMessageSummary from(IntegrationInboxMessage message) {
            return new IntegrationMessageSummary(message.id(), message.sourceSystem(), message.idempotencyKey(),
                    message.eventType(), message.siteCode().value(), message.status(), message.attempts(),
                    message.receivedAt(), message.processedAt());
        }
    }
}
