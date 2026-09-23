package gh.edu.clet.sfl.safetysecurity.cctv.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A video-analytics alert raised to the SOC queue - SRS-SFL-S161-04. Can, in turn, seed a security
 * incident (S163, {@code IncidentSource.CCTV_SEED}) - see {@code IncidentSeedingPort}. Mirrors
 * {@code accesscontrol.domain.model.AccessException}'s shape.
 */
public record AnalyticsAlert(UUID id, String siteCode, String cameraId, AlertType type, AlertSeverity severity,
        AlertStatus status, Instant occurredAt, UUID seededIncidentId, Instant siemForwardedAt,
        RecordMetadata metadata) {

    public AnalyticsAlert {
        Objects.requireNonNull(id, "id is required");
        require(siteCode, "siteCode");
        require(cameraId, "cameraId");
        Objects.requireNonNull(type, "type is required");
        Objects.requireNonNull(severity, "severity is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static AnalyticsAlert raise(UUID id, String siteCode, String cameraId, AlertType type,
            AlertSeverity severity, Instant occurredAt, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new AnalyticsAlert(id, siteCode, cameraId, type, severity, AlertStatus.OPEN, occurredAt, null, null,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    public AnalyticsAlert acknowledge(String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new AnalyticsAlert(id, siteCode, cameraId, type, severity, AlertStatus.ACKNOWLEDGED, occurredAt,
                seededIncidentId, siemForwardedAt, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public AnalyticsAlert resolve(String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new AnalyticsAlert(id, siteCode, cameraId, type, severity, AlertStatus.RESOLVED, occurredAt,
                seededIncidentId, siemForwardedAt, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public AnalyticsAlert withSeededIncident(UUID incidentId, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new AnalyticsAlert(id, siteCode, cameraId, type, severity, status, occurredAt, incidentId,
                siemForwardedAt, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public AnalyticsAlert withSiemForwarded(Instant forwardedAt, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new AnalyticsAlert(id, siteCode, cameraId, type, severity, status, occurredAt, seededIncidentId,
                forwardedAt, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
