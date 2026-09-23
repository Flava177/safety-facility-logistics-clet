package gh.edu.clet.sfl.safetysecurity.intrusion.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Current device-health state for one intrusion panel - SRS-SFL-S162-01/04. One row per
 * {@code (siteCode, panelId)}, upserted on every health/fault signal the panel or monitoring feed
 * reports - mirrors {@code accesscontrol.domain.model.ReaderHealth}. */
public record PanelHealth(UUID id, String siteCode, String panelId, String zoneCode, PanelHealthStatus status,
        Instant lastSeenAt, RecordMetadata metadata) {

    public PanelHealth {
        Objects.requireNonNull(id, "id is required");
        require(siteCode, "siteCode");
        require(panelId, "panelId");
        require(zoneCode, "zoneCode");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt is required");
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static PanelHealth report(UUID id, String siteCode, String panelId, String zoneCode,
            PanelHealthStatus status, Instant lastSeenAt, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new PanelHealth(id, siteCode, panelId, zoneCode, status, lastSeenAt,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    public PanelHealth update(PanelHealthStatus newStatus, Instant lastSeenAt, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new PanelHealth(id, siteCode, panelId, zoneCode, newStatus, lastSeenAt,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
