package gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Current device-health state for one reader/door controller - SRS-SFL-S160a-04. One row per
 * {@code (siteCode, readerId)}, upserted on every health signal the vendor system reports. */
public record ReaderHealth(UUID id, String siteCode, String readerId, String zoneCode, ReaderHealthStatus status,
        Instant lastSeenAt, RecordMetadata metadata) {

    public ReaderHealth {
        Objects.requireNonNull(id, "id is required");
        require(siteCode, "siteCode");
        require(readerId, "readerId");
        require(zoneCode, "zoneCode");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt is required");
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static ReaderHealth report(UUID id, String siteCode, String readerId, String zoneCode,
            ReaderHealthStatus status, Instant lastSeenAt, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new ReaderHealth(id, siteCode, readerId, zoneCode, status, lastSeenAt,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    public ReaderHealth update(ReaderHealthStatus newStatus, Instant lastSeenAt, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new ReaderHealth(id, siteCode, readerId, zoneCode, newStatus, lastSeenAt,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
