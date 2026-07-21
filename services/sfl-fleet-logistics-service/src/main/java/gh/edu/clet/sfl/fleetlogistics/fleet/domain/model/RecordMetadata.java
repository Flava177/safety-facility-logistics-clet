package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * The SRS-SFL-S166-01 system-managed field set carried by every fleet operational aggregate:
 * created by/date, last modified by/date, optimistic-lock version, source channel and audit
 * correlation ID. (The record UUID and site scope live on the aggregate itself.)
 *
 * <p>{@code version} is the optimistic-lock version as last read; the persistence adapter owns
 * incrementing it, so domain code never guesses a version number.
 */
public record RecordMetadata(
        String createdBy,
        Instant createdAt,
        String lastModifiedBy,
        Instant lastModifiedAt,
        long version,
        SourceChannel sourceChannel,
        String auditCorrelationId) {

    public RecordMetadata {
        createdBy = requireActor(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt is required");
        lastModifiedBy = requireActor(lastModifiedBy, "lastModifiedBy");
        Objects.requireNonNull(lastModifiedAt, "lastModifiedAt is required");
        Objects.requireNonNull(sourceChannel, "sourceChannel is required");
        if (version < 0) {
            throw new IllegalArgumentException("version cannot be negative");
        }
        if (lastModifiedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("lastModifiedAt cannot precede createdAt");
        }
    }

    /** Metadata for a record being created now. */
    public static RecordMetadata createdBy(String actorId, Instant now, SourceChannel channel, String correlationId) {
        return new RecordMetadata(actorId, now, actorId, now, 0L, channel, correlationId);
    }

    /** Metadata after an update by {@code actorId}; the persistence layer supplies the new version. */
    public RecordMetadata modifiedBy(String actorId, Instant now, SourceChannel channel, String correlationId) {
        return new RecordMetadata(createdBy, createdAt, actorId, now, version, channel, correlationId);
    }

    /** Rehydrates metadata read from storage, preserving the stored version. */
    public static RecordMetadata rehydrate(String createdBy, Instant createdAt, String lastModifiedBy,
            Instant lastModifiedAt, long version, SourceChannel sourceChannel, String auditCorrelationId) {
        return new RecordMetadata(createdBy, createdAt, lastModifiedBy, lastModifiedAt, version, sourceChannel,
                auditCorrelationId);
    }

    private static String requireActor(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
