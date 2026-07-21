package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IdempotencyPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.IdempotencyKeyConflictException;
import gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.audit.CanonicalJson;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotency store for retried state-creating commands.
 *
 * <p>A replay with the same key and the same payload returns the original identifier. A replay with the
 * same key but a different payload is rejected rather than silently creating a second record — the
 * client has a bug and hiding it would corrupt the register.
 */
@Component
class JpaIdempotencyAdapter implements IdempotencyPort {

    private final IdempotencyKeyRepository repository;
    private final Clock clock;

    JpaIdempotencyAdapter(IdempotencyKeyRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findExistingResult(String operation, String idempotencyKey, String requestFingerprint) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return repository.findByOperationAndIdempotencyKey(operation, idempotencyKey)
                .map(existing -> {
                    if (!existing.requestFingerprint().equals(requestFingerprint)) {
                        throw new IdempotencyKeyConflictException(Map.of(
                                "operation", operation,
                                "idempotencyKey", idempotencyKey));
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
        repository.save(new IdempotencyKeyEntity(UUID.randomUUID(), operation, idempotencyKey, requestFingerprint,
                resultId, siteCode, actorId, clock.instant()));
    }

    @Override
    public String fingerprint(Object requestPayload) {
        String canonical = CanonicalJson.write(requestPayload);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((canonical == null ? "" : canonical).getBytes(StandardCharsets.UTF_8));
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
