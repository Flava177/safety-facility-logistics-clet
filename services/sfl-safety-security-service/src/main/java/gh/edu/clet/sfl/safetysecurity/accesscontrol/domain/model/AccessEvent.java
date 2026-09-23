package gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One normalised, ingested access-control event - SRS-SFL-S160a-01. Stored exactly once per
 * {@code (source, externalEventId)} - the inbox in front of {@code AccessEventIngestionService}
 * guarantees that, so this record itself does not re-check idempotency.
 *
 * @param direction only meaningful for {@link AccessEventKind#GRANTED}; {@link AccessDirection#UNKNOWN}
 *        for every other kind or where the vendor system reports no direction.
 * @param personRef the vendor's identifier for the credential holder, where the event carries one -
 *        null for a tamper or health-only signal.
 */
public record AccessEvent(UUID id, String siteCode, String source, String externalEventId, String readerId,
        String doorId, String zoneCode, String personRef, AccessEventKind kind, AccessDirection direction,
        Instant occurredAt, RecordMetadata metadata) {

    public AccessEvent {
        Objects.requireNonNull(id, "id is required");
        require(siteCode, "siteCode");
        require(source, "source");
        require(externalEventId, "externalEventId");
        require(readerId, "readerId");
        doorId = blankToNull(doorId);
        require(zoneCode, "zoneCode");
        personRef = blankToNull(personRef);
        Objects.requireNonNull(kind, "kind is required");
        direction = kind == AccessEventKind.GRANTED && direction != null ? direction : AccessDirection.UNKNOWN;
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static AccessEvent ingest(UUID id, String siteCode, String source, String externalEventId,
            String readerId, String doorId, String zoneCode, String personRef, AccessEventKind kind,
            AccessDirection direction, Instant occurredAt, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new AccessEvent(id, siteCode, source, externalEventId, readerId, doorId, zoneCode, personRef, kind,
                direction, occurredAt, RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
