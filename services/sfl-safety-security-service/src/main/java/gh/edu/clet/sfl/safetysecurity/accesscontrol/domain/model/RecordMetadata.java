package gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model;

import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.exception.AccessControlErrorCode;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.exception.AccessControlException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** The system-managed field set carried by every S160a aggregate - mirrors {@code visitor.domain.model.RecordMetadata}. */
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

    public RecordMetadata modifiedBy(String actorId, Instant now, SourceChannel channel, String correlationId) {
        return new RecordMetadata(createdBy, createdAt, actorId, now, version + 1, channel, correlationId);
    }

    public static RecordMetadata rehydrate(String createdBy, Instant createdAt, String lastModifiedBy,
            Instant lastModifiedAt, long version, SourceChannel sourceChannel, String correlationId) {
        return new RecordMetadata(createdBy, createdAt, lastModifiedBy, lastModifiedAt, version, sourceChannel,
                correlationId);
    }

    public void requireVersion(Long expectedVersion) {
        if (expectedVersion != null && expectedVersion != version) {
            throw new AccessControlException(AccessControlErrorCode.ACCESS_CONTROL_RECORD_VERSION_CONFLICT,
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
