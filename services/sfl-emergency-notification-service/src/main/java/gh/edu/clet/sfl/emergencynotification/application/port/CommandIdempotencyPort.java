package gh.edu.clet.sfl.emergencynotification.application.port;

import java.util.Optional;
import java.util.UUID;

/** Deduplicates retried state-creating commands carrying an Idempotency-Key header. */
public interface CommandIdempotencyPort {

    Optional<UUID> findExistingResult(String operation, String idempotencyKey, String requestFingerprint);

    void recordResult(String operation, String idempotencyKey, String requestFingerprint, UUID resultId,
            String siteCode, String actorId);

    String fingerprint(Object requestPayload);
}
