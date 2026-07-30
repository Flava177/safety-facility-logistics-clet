package gh.edu.clet.sfl.facilities.shared.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.shared.application.port.IdempotencyPort;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Postgres-backed idempotency store (SRS-SFL-S152-04).
 *
 * <p>The fingerprint is what makes the store safe. Matching key <em>and</em> matching payload is a
 * retry and returns the original identifier; matching key with a different payload is
 * {@code IDEMPOTENCY_KEY_CONFLICT}, because silently returning the first record's id for a second,
 * different request would create a record the caller never sees and hand them somebody else's.
 */
@Component
class JpaIdempotencyAdapter implements IdempotencyPort {

    private final IdempotencyKeyRepository keys;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    JpaIdempotencyAdapter(IdempotencyKeyRepository keys, ObjectMapper objectMapper, Clock clock) {
        this.keys = keys;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findExistingResult(String operation, String idempotencyKey, String requestFingerprint) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return keys.findByOperationAndIdempotencyKey(operation, idempotencyKey)
                .map(existing -> {
                    if (!existing.requestFingerprint().equals(requestFingerprint)) {
                        throw new FacilitiesException.IdempotencyKeyConflictException(operation, idempotencyKey);
                    }
                    return existing.resultId();
                });
    }

    @Override
    @Transactional
    public void recordResult(String operation, String idempotencyKey, String requestFingerprint, UUID resultId,
            String siteCode, String actorId) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        keys.save(new IdempotencyKeyEntity(UUID.randomUUID(), operation, idempotencyKey, requestFingerprint,
                resultId, siteCode, actorId, clock.instant()));
    }

    @Override
    public String fingerprint(Object requestPayload) {
        try {
            return sha256Hex(objectMapper.writeValueAsString(requestPayload));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not fingerprint the request payload", exception);
        }
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available in this runtime", exception);
        }
    }
}
