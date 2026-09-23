package gh.edu.clet.sfl.safetysecurity.intrusion.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The raw, normalised signal log - SRS-SFL-S162-01. Every inbound panel signal is stored here first,
 * whether or not it goes on to raise or close an {@link IntrusionAlarm} - mirrors
 * {@code accesscontrol.domain.model.AccessEvent}'s split between the raw log and the derived
 * SOC-queue aggregate.
 */
public record IntrusionSignal(UUID id, String siteCode, String source, String externalEventId, String panelId,
        String zoneCode, SignalType signalType, Instant occurredAt, RecordMetadata metadata) {

    public IntrusionSignal {
        Objects.requireNonNull(id, "id is required");
        require(siteCode, "siteCode");
        require(source, "source");
        require(externalEventId, "externalEventId");
        require(panelId, "panelId");
        require(zoneCode, "zoneCode");
        Objects.requireNonNull(signalType, "signalType is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static IntrusionSignal ingest(UUID id, String siteCode, String source, String externalEventId,
            String panelId, String zoneCode, SignalType signalType, Instant occurredAt, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new IntrusionSignal(id, siteCode, source, externalEventId, panelId, zoneCode, signalType, occurredAt,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
