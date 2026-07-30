package gh.edu.clet.sfl.facilities.shared.domain.audit;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One append-only audit record (SRS-SFL-S152-03).
 *
 * <p>Carries everything the requirement names: actor, timestamp, before/after values, source channel
 * and correlation ID — plus the sequence number and hash pair that make the chain replay-verifiable.
 *
 * <p>{@code sequenceNo}, {@code previousHash} and {@code recordHash} are unset at construction and
 * filled by {@link AuditHashChain#seal}, because a record cannot know its own position until the
 * writer has taken the chain-head lock.
 */
public record AuditEvent(
        UUID id,
        long sequenceNo,
        String siteScope,
        String actorId,
        String actorDisplayName,
        AuditAction action,
        String resourceType,
        String resourceId,
        String beforeValue,
        String afterValue,
        String correlationId,
        SourceChannel sourceChannel,
        Instant occurredAt,
        String previousHash,
        String recordHash) {

    public AuditEvent {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(action, "action is required");
        Objects.requireNonNull(sourceChannel, "sourceChannel is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        siteScope = requireText(siteScope, "siteScope");
        actorId = requireText(actorId, "actorId");
        actorDisplayName = actorDisplayName == null || actorDisplayName.isBlank()
                ? actorId
                : actorDisplayName.strip();
        resourceType = requireText(resourceType, "resourceType");
        resourceId = requireText(resourceId, "resourceId");
    }

    /**
     * An unsealed record — no position in the chain yet.
     *
     * @param siteScope the site the change belongs to, or {@code *} for a platform-wide change
     */
    public static AuditEvent of(UUID id, String siteScope, String actorId, String actorDisplayName,
            AuditAction action, String resourceType, String resourceId, String beforeValue, String afterValue,
            String correlationId, SourceChannel sourceChannel, Instant occurredAt) {
        return new AuditEvent(id, 0L, siteScope, actorId, actorDisplayName, action, resourceType, resourceId,
                beforeValue, afterValue, correlationId, sourceChannel, occurredAt, null, null);
    }

    /** The same record positioned in the chain. */
    public AuditEvent sealed(long sequence, String previous, String hash) {
        return new AuditEvent(id, sequence, siteScope, actorId, actorDisplayName, action, resourceType,
                resourceId, beforeValue, afterValue, correlationId, sourceChannel, occurredAt, previous, hash);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
