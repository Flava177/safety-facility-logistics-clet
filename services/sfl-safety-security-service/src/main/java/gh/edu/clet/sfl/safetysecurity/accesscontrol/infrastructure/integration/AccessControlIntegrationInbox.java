package gh.edu.clet.sfl.safetysecurity.accesscontrol.infrastructure.integration;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.exception.AccessControlErrorCode;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.exception.AccessControlException;
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
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Secure inbound-message intake (SRS-SFL-S160a-01): source allowlist, HMAC signature + timestamp
 * window, schema validation and idempotency, persisting the inbox envelope BEFORE any domain side
 * effect. Mirrors {@code EmergencyIntegrationInbox}'s mechanism, with one deliberate simplification:
 * the allowlist and per-source secret come from static configuration ({@code sfl.access-control.*}
 * properties) rather than {@code RuntimeConfigurationPort}, which is an S174-owned runtime-config
 * subsystem (its own tables, its own per-site override screens) that S160a's two inbound sources
 * (the access-control vendor and HRMS/IAM) do not yet need - a runtime-configurable allowlist is a
 * real feature this module can grow into, not a gap in this pass.
 */
@Component
public class AccessControlIntegrationInbox {

    private static final Duration SIGNATURE_WINDOW = Duration.ofMinutes(5);

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Set<String> allowlistedSources;
    private final String sharedSecret;

    /**
     * One shared signing secret across both inbound sources (the access-control vendor and HRMS/IAM),
     * not a per-source map: this is a development-time placeholder either way (see the class javadoc),
     * and a per-source secret map is a config-shape change a real integration can make without touching
     * this class's logic, once a real vendor and a real secret exist to configure it with.
     */
    public AccessControlIntegrationInbox(JdbcTemplate jdbc, Clock clock,
            @Value("${sfl.access-control.integration.allowed-sources:ACCESS-CONTROL-VENDOR,HRMS-IAM}")
            List<String> allowlistedSources,
            @Value("${sfl.access-control.integration.shared-secret:dev-only-not-a-real-secret}")
            String sharedSecret) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.allowlistedSources = allowlistedSources.stream().map(s -> s.strip().toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.sharedSecret = sharedSecret;
    }

    public record InboundMessage(String source, String idempotencyKey, String eventType, String siteCode,
            Instant signedAt, String signature, String rawPayload, Map<String, Object> payload,
            List<String> requiredFields, ActorContext actor) {
    }

    /** Accepts and persists a verified inbound message, returning its inbox id. Throws on any rejection. */
    @Transactional
    public UUID accept(InboundMessage message) {
        String source = normalise(message.source());
        requireAllowlisted(source);
        requireValidSignature(source, message.signedAt(), message.rawPayload(), message.signature());
        validateSchema(message);
        if (message.idempotencyKey() == null || message.idempotencyKey().isBlank()) {
            throw new AccessControlException(AccessControlErrorCode.ACCESS_SCHEMA_VALIDATION_FAILED,
                    Map.of("field", "idempotencyKey"));
        }
        Long existing = jdbc.queryForObject("""
                SELECT COUNT(*) FROM safety_security.access_control_inbox_messages
                WHERE source_system=? AND idempotency_key=?
                """, Long.class, source, message.idempotencyKey());
        if (existing != null && existing > 0) {
            throw new AccessControlException(AccessControlErrorCode.ACCESS_DUPLICATE_MESSAGE,
                    Map.of("sourceSystem", source, "idempotencyKey", message.idempotencyKey()));
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO safety_security.access_control_inbox_messages (id,source_system,idempotency_key,
                    event_type,site_scope,payload_hash,raw_payload,status,received_at,processed_at,correlation_id)
                VALUES (?,?,?,?,?,?,?,'PROCESSED',?,?,?)
                """, id, source, message.idempotencyKey(), message.eventType(), message.siteCode(),
                sha256(message.rawPayload()), message.rawPayload(),
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                message.actor() == null ? null : message.actor().correlationId());
        return id;
    }

    private void requireAllowlisted(String source) {
        if (!allowlistedSources.contains(source)) {
            throw new AccessControlException(AccessControlErrorCode.ACCESS_SOURCE_NOT_ALLOWED,
                    Map.of("sourceSystem", source));
        }
    }

    private void requireValidSignature(String source, Instant signedAt, String rawPayload, String signature) {
        String secret = sharedSecret;
        if (signedAt == null || Duration.between(signedAt, clock.instant()).abs().compareTo(SIGNATURE_WINDOW) > 0) {
            throw new AccessControlException(AccessControlErrorCode.ACCESS_INVALID_SIGNATURE,
                    Map.of("sourceSystem", source, "reason", "timestamp outside window"));
        }
        String expected = hmac(secret, signedAt + "." + rawPayload);
        if (signature == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.strip().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8))) {
            throw new AccessControlException(AccessControlErrorCode.ACCESS_INVALID_SIGNATURE,
                    Map.of("sourceSystem", source, "reason", "HMAC mismatch"));
        }
    }

    private void validateSchema(InboundMessage message) {
        Map<String, Object> payload = message.payload();
        if (message.eventType() == null || message.eventType().isBlank() || message.siteCode() == null
                || message.siteCode().isBlank() || payload == null || payload.isEmpty()) {
            throw new AccessControlException(AccessControlErrorCode.ACCESS_SCHEMA_VALIDATION_FAILED,
                    Map.of("sourceSystem", message.source()));
        }
        for (String field : message.requiredFields()) {
            Object value = payload.get(field);
            if (value == null || String.valueOf(value).isBlank()) {
                throw new AccessControlException(AccessControlErrorCode.ACCESS_SCHEMA_VALIDATION_FAILED,
                        Map.of("field", field));
            }
        }
    }

    private static String normalise(String source) {
        if (source == null || source.isBlank()) {
            throw new AccessControlException(AccessControlErrorCode.ACCESS_SOURCE_NOT_ALLOWED,
                    Map.of("sourceSystem", ""));
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
