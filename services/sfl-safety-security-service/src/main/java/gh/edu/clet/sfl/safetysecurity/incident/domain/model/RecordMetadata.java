package gh.edu.clet.sfl.safetysecurity.incident.domain.model;

import gh.edu.clet.sfl.safetysecurity.incident.domain.exception.IncidentErrorCode;
import gh.edu.clet.sfl.safetysecurity.incident.domain.exception.IncidentException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * The system-managed field set carried by every operational aggregate: created by/date, last modified
 * by/date, optimistic-lock version, source channel and audit correlation ID.
 *
 * <p>Own copy per module, following the same JPA-backed, application-managed {@code record_version}
 * convention {@code visitor.domain.model.RecordMetadata} uses (not S174's plain-JDBC version-in-SQL
 * convention) - this module is JPA-backed the same way S160 is.
 */
public record RecordMetadata(String createdBy, Instant createdAt, String lastModifiedBy, Instant lastModifiedAt,
        long version, SourceChannel sourceChannel, String correlationId) {

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

    public static RecordMetadata createdBy(String actorId, Instant now, SourceChannel channel, String correlationId) {
        return new RecordMetadata(actorId, now, actorId, now, 0L, channel, correlationId);
    }

    /** Provenance after a change: same creation facts, new modifier, next version. */
    public RecordMetadata modifiedBy(String actorId, Instant now, SourceChannel channel, String correlationId) {
        return new RecordMetadata(createdBy, createdAt, actorId, now, version + 1, channel, correlationId);
    }

    public static RecordMetadata rehydrate(String createdBy, Instant createdAt, String lastModifiedBy,
            Instant lastModifiedAt, long version, SourceChannel sourceChannel, String correlationId) {
        return new RecordMetadata(createdBy, createdAt, lastModifiedBy, lastModifiedAt, version, sourceChannel,
                correlationId);
    }

    /**
     * The check a command runs before applying a change, turning a lost update into
     * {@code INCIDENT_RECORD_VERSION_CONFLICT} rather than a silent overwrite of somebody else's edit.
     * A null {@code expectedVersion} means the caller does not care and skips the check.
     */
    public void requireVersion(Long expectedVersion) {
        if (expectedVersion != null && expectedVersion != version) {
            throw new IncidentException(IncidentErrorCode.INCIDENT_RECORD_VERSION_CONFLICT,
                    Map.of("expectedVersion", expectedVersion, "actualVersion", version));
        }
    }

    private static String requireActor(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
