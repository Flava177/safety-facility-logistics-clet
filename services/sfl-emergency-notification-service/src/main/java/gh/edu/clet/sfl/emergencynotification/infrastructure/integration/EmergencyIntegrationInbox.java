package gh.edu.clet.sfl.emergencynotification.infrastructure.integration;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.emergencynotification.application.port.RuntimeConfigurationPort;
import gh.edu.clet.sfl.emergencynotification.domain.exception.EmergencyErrorCode;
import gh.edu.clet.sfl.emergencynotification.domain.exception.EmergencyException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Secure inbound-message intake (SRS-SFL-S174-04): source allowlist, HMAC signature + timestamp window,
 * schema validation and idempotency, persisting the inbox envelope BEFORE any domain side effect. Unsigned,
 * untrusted, schema-invalid or duplicate payloads are rejected with the SRS error codes before processing.
 */
@Component
public class EmergencyIntegrationInbox {

    private static final Duration SIGNATURE_WINDOW = Duration.ofMinutes(5);

    private final JdbcTemplate jdbc;
    private final RuntimeConfigurationPort configuration;
    private final Clock clock;

    public EmergencyIntegrationInbox(JdbcTemplate jdbc, RuntimeConfigurationPort configuration, Clock clock) {
        this.jdbc = jdbc;
        this.configuration = configuration;
        this.clock = clock;
    }

    public record InboundMessage(String source, String idempotencyKey, String eventType, String siteCode,
            Instant signedAt, String signature, String rawPayload, Map<String, Object> payload,
            List<String> requiredFields, ActorContext actor) {
    }

    /** Accepts and persists a verified inbound message, returning its inbox id. Throws on any rejection. */
    @Transactional
    public UUID accept(InboundMessage message) {
        String source = normalise(message.source());
        requireAllowlisted(source, message.siteCode());
        requireValidSignature(source, message.siteCode(), message.signedAt(), message.rawPayload(),
                message.signature());
        validateSchema(message);
        if (message.idempotencyKey() == null || message.idempotencyKey().isBlank()) {
            throw new EmergencyException(EmergencyErrorCode.EMERGENCY_SCHEMA_VALIDATION_FAILED,
                    Map.of("field", "idempotencyKey"));
        }
        Long existing = jdbc.queryForObject("""
                SELECT COUNT(*) FROM emergency_notification.integration_inbox_messages
                WHERE source_system=? AND idempotency_key=?
                """, Long.class, source, message.idempotencyKey());
        if (existing != null && existing > 0) {
            throw new EmergencyException(EmergencyErrorCode.EMERGENCY_DUPLICATE_MESSAGE,
                    Map.of("sourceSystem", source, "idempotencyKey", message.idempotencyKey()));
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO emergency_notification.integration_inbox_messages (id,source_system,idempotency_key,
                    event_type,site_scope,payload_hash,raw_payload,status,attempts,received_at,processed_at,
                    correlation_id)
                VALUES (?,?,?,?,?,?,?,'PROCESSED',1,?,?,?)
                """, id, source, message.idempotencyKey(), message.eventType(), message.siteCode(),
                sha256(message.rawPayload()), message.rawPayload(),
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                message.actor() == null ? null : message.actor().correlationId());
        return id;
    }

    private void requireAllowlisted(String source, String siteCode) {
        if (!configuration.flag("emergency.integration." + source + ".enabled", siteCode, false)) {
            throw new EmergencyException(EmergencyErrorCode.EMERGENCY_SOURCE_NOT_ALLOWED,
                    Map.of("sourceSystem", source, "siteCode", siteCode));
        }
    }

    private void requireValidSignature(String source, String siteCode, Instant signedAt, String rawPayload,
            String signature) {
        String secret = configuration.value("emergency.integration." + source + ".secret", siteCode)
                .orElseThrow(() -> new EmergencyException(EmergencyErrorCode.EMERGENCY_INTEGRATION_NOT_CONFIGURED,
                        Map.of("sourceSystem", source, "siteCode", siteCode)));
        if (signedAt == null || Duration.between(signedAt, clock.instant()).abs().compareTo(SIGNATURE_WINDOW) > 0) {
            throw new EmergencyException(EmergencyErrorCode.EMERGENCY_INVALID_SIGNATURE,
                    Map.of("sourceSystem", source, "reason", "timestamp outside window"));
        }
        String expected = hmac(secret, signedAt + "." + rawPayload);
        if (signature == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.strip().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8))) {
            throw new EmergencyException(EmergencyErrorCode.EMERGENCY_INVALID_SIGNATURE,
                    Map.of("sourceSystem", source, "reason", "HMAC mismatch"));
        }
    }

    private void validateSchema(InboundMessage message) {
        Map<String, Object> payload = message.payload();
        if (message.eventType() == null || message.eventType().isBlank() || message.siteCode() == null
                || message.siteCode().isBlank() || payload == null || payload.isEmpty()) {
            throw new EmergencyException(EmergencyErrorCode.EMERGENCY_SCHEMA_VALIDATION_FAILED,
                    Map.of("sourceSystem", message.source()));
        }
        for (String field : message.requiredFields()) {
            Object value = payload.get(field);
            if (value == null || String.valueOf(value).isBlank()) {
                throw new EmergencyException(EmergencyErrorCode.EMERGENCY_SCHEMA_VALIDATION_FAILED,
                        Map.of("field", field));
            }
        }
    }

    private static String normalise(String source) {
        if (source == null || source.isBlank()) {
            throw new EmergencyException(EmergencyErrorCode.EMERGENCY_SOURCE_NOT_ALLOWED, Map.of("sourceSystem", ""));
        }
        return source.strip().toUpperCase(Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String hmac(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
