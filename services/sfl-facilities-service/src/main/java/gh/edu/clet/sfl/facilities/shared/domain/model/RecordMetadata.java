package gh.edu.clet.sfl.facilities.shared.domain.model;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.time.Instant;
import java.util.Objects;

/**
 * The system-managed fields SRS-SFL-S152-01 requires on every operational record:
 * "created by/date, last modified by/date, version, source channel and audit correlation ID".
 *
 * <p>One value object rather than six columns copied onto seven aggregates. Every mutation goes
 * through {@link #modifiedBy}, which increments the version — so an aggregate cannot be changed
 * without its provenance moving with it, and forgetting to bump the version is not a thing a
 * developer can do by omission.
 *
 * <p>The version is the optimistic lock. {@link #requireVersion} is the check a command runs before
 * applying a change, turning a lost update into the {@code VERSION_CONFLICT} error state rather than
 * a silent overwrite of somebody else's edit.
 */
public record RecordMetadata(
        String createdBy,
        Instant createdAt,
        String lastModifiedBy,
        Instant lastModifiedAt,
        long version,
        SourceChannel sourceChannel,
        String correlationId) {

    public RecordMetadata {
        createdBy = requireActor(createdBy);
        Objects.requireNonNull(createdAt, "createdAt is required");
        lastModifiedBy = lastModifiedBy == null || lastModifiedBy.isBlank() ? createdBy : lastModifiedBy.strip();
        lastModifiedAt = lastModifiedAt == null ? createdAt : lastModifiedAt;
        Objects.requireNonNull(sourceChannel, "sourceChannel is required");
        if (version < 0) {
            throw new IllegalArgumentException("version cannot be negative");
        }
    }

    /** Provenance for a newly created record. Version starts at zero. */
    public static RecordMetadata createdBy(String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new RecordMetadata(actorId, at, actorId, at, 0L, channel, correlationId);
    }

    /** Provenance after a change: same creation facts, new modifier, next version. */
    public RecordMetadata modifiedBy(String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new RecordMetadata(createdBy, createdAt, requireActor(actorId), at, version + 1,
                channel == null ? sourceChannel : channel,
                correlationId == null || correlationId.isBlank() ? this.correlationId : correlationId);
    }

    /**
     * Rejects a write built on a stale read.
     *
     * <p>A {@code null} expected version means the caller did not supply {@code If-Match} semantics and
     * accepts last-write-wins. That is deliberate: forcing a version onto every PATCH would break the
     * dashboard's simple toggles for no benefit, while a caller that *does* care can always opt in.
     */
    public void requireVersion(Long expectedVersion, String recordType, Object recordId) {
        if (expectedVersion != null && expectedVersion != version) {
            throw new FacilitiesException.VersionConflictException(recordType, recordId, expectedVersion, version);
        }
    }

    private static String requireActor(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("actor is required on a record's provenance");
        }
        return value.strip();
    }
}
