package gh.edu.clet.sfl.emergencynotification.infrastructure.persistence;

import gh.edu.clet.sfl.emergencynotification.application.port.CommandIdempotencyPort;
import gh.edu.clet.sfl.emergencynotification.domain.exception.EmergencyErrorCode;
import gh.edu.clet.sfl.emergencynotification.domain.exception.EmergencyException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Component
class JdbcCommandIdempotencyAdapter implements CommandIdempotencyPort {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    JdbcCommandIdempotencyAdapter(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findExistingResult(String operation, String idempotencyKey, String requestFingerprint) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        var rows = jdbc.query("""
                SELECT result_id, request_fingerprint
                FROM emergency_notification.command_idempotency_keys
                WHERE operation=? AND idempotency_key=?
                """, (rs, n) -> new Existing(rs.getObject("result_id", UUID.class),
                rs.getString("request_fingerprint")), operation, idempotencyKey.strip());
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Existing existing = rows.get(0);
        if (!existing.requestFingerprint().equals(requestFingerprint)) {
            throw new EmergencyException(EmergencyErrorCode.EMERGENCY_IDEMPOTENCY_KEY_CONFLICT,
                    Map.of("operation", operation, "idempotencyKey", idempotencyKey));
        }
        return Optional.of(existing.resultId());
    }

    @Override
    @Transactional
    public void recordResult(String operation, String idempotencyKey, String requestFingerprint, UUID resultId,
            String siteCode, String actorId) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        jdbc.update("""
                INSERT INTO emergency_notification.command_idempotency_keys
                    (id,operation,idempotency_key,request_fingerprint,result_id,site_code,actor_id,created_at)
                VALUES (?,?,?,?,?,?,?,?)
                ON CONFLICT (operation, idempotency_key) DO NOTHING
                """, UUID.randomUUID(), operation, idempotencyKey.strip(), requestFingerprint, resultId, siteCode,
                actorId, OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }

    @Override
    public String fingerprint(Object requestPayload) {
        try {
            return sha256(json.writeValueAsString(requestPayload));
        } catch (RuntimeException exception) {
            return sha256(String.valueOf(requestPayload));
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available in this runtime", exception);
        }
    }

    private record Existing(UUID resultId, String requestFingerprint) {
    }
}
