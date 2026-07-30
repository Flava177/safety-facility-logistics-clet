package gh.edu.clet.sfl.facilities.shared.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Dedup store for retried state-creating commands carrying an {@code Idempotency-Key} header
 * (SRS-SFL-S152-04, "Integration messages shall carry idempotency keys and correlation IDs").
 *
 * <p>A replay of the same key <em>and</em> the same request payload returns the original result; a
 * replay with a different payload is a client error, not a silent overwrite. That distinction is the
 * whole value of the store — without the fingerprint, a reused key would quietly return the wrong
 * record's identifier.
 */
public interface IdempotencyPort {

    /**
     * The identifier produced by a previous execution of this key.
     *
     * @throws gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException.IdempotencyKeyConflictException
     *         when the key was used before with a different payload
     */
    Optional<UUID> findExistingResult(String operation, String idempotencyKey, String requestFingerprint);

    /** Records the outcome of a successfully executed command so a retry can return it. */
    void recordResult(String operation, String idempotencyKey, String requestFingerprint, UUID resultId,
            String siteCode, String actorId);

    /** Stable fingerprint of a request payload, used to detect key reuse with different content. */
    String fingerprint(Object requestPayload);
}
