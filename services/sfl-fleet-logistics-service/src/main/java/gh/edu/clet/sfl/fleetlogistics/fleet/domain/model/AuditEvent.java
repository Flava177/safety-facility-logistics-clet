package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One append-only audit entry (SRS-SFL-S166-03).
 *
 * <p>Carries actor, timestamp, action, source channel, before/after values and correlation ID, and the
 * two hash-chain fields that make the log tamper-evident. {@code beforeValue}/{@code afterValue} are
 * canonical JSON produced by the application layer — the domain treats them as opaque text so that no
 * JSON library is needed here.
 *
 * <p>A record is created "unsealed" ({@code recordHash == null}) and sealed by {@link AuditHashChain}
 * once its position in the chain is known.
 */
public record AuditEvent(
        UUID id,
        long sequenceNo,
        SiteCode siteScope,
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
        Objects.requireNonNull(siteScope, "siteScope is required");
        Objects.requireNonNull(action, "action is required");
        Objects.requireNonNull(sourceChannel, "sourceChannel is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        actorId = requireText(actorId, "actorId");
        resourceType = requireText(resourceType, "resourceType");
        resourceId = requireText(resourceId, "resourceId");
        actorDisplayName = actorDisplayName == null || actorDisplayName.isBlank()
                ? actorId
                : actorDisplayName.strip();
        if (sequenceNo < 0) {
            throw new IllegalArgumentException("sequenceNo cannot be negative");
        }
    }

    /**
     * Creates an entry that has not yet been placed in the chain. {@code sequenceNo}, {@code previousHash}
     * and {@code recordHash} are assigned when the writer seals it under the chain lock.
     */
    public static AuditEvent unsealed(UUID id, SiteCode siteScope, String actorId, String actorDisplayName,
            AuditAction action, String resourceType, String resourceId, String beforeValue, String afterValue,
            String correlationId, SourceChannel sourceChannel, Instant occurredAt) {
        return new AuditEvent(id, 0L, siteScope, actorId, actorDisplayName, action, resourceType, resourceId,
                beforeValue, afterValue, correlationId, sourceChannel, occurredAt, null, null);
    }

    AuditEvent sealed(long assignedSequenceNo, String previousChainHash, String hash) {
        return new AuditEvent(id, assignedSequenceNo, siteScope, actorId, actorDisplayName, action, resourceType,
                resourceId, beforeValue, afterValue, correlationId, sourceChannel, occurredAt, previousChainHash, hash);
    }

    public boolean isSealed() {
        return recordHash != null && previousHash != null;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
