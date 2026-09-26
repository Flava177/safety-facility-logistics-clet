package gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A security-relevant access exception raised to the SOC queue - SRS-SFL-S160a-04. May be seeded from
 * an {@link AccessEvent} (a denial, forced-open, tailgating indicator) or from a reader-health signal
 * (offline/tampered - {@link ExceptionRuleCode#READER_OFFLINE}), or from an anti-passback detection
 * (S160a-06). Can, in turn, seed a security incident (S163) - see {@code AccessControlIncidentSeedingPort}.
 */
public record AccessException(UUID id, String siteCode, UUID eventId, String readerId, String zoneCode,
        ExceptionRuleCode ruleCode, ExceptionSeverity severity, ExceptionStatus status, UUID seededIncidentId,
        Instant siemForwardedAt, RecordMetadata metadata) {

    public AccessException {
        Objects.requireNonNull(id, "id is required");
        require(siteCode, "siteCode");
        readerId = blankToNull(readerId);
        require(zoneCode, "zoneCode");
        Objects.requireNonNull(ruleCode, "ruleCode is required");
        Objects.requireNonNull(severity, "severity is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static AccessException raise(UUID id, String siteCode, UUID eventId, String readerId, String zoneCode,
            ExceptionRuleCode ruleCode, ExceptionSeverity severity, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new AccessException(id, siteCode, eventId, readerId, zoneCode, ruleCode, severity,
                ExceptionStatus.OPEN, null, null, RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    public AccessException acknowledge(String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new AccessException(id, siteCode, eventId, readerId, zoneCode, ruleCode, severity,
                ExceptionStatus.ACKNOWLEDGED, seededIncidentId, siemForwardedAt,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public AccessException resolve(String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new AccessException(id, siteCode, eventId, readerId, zoneCode, ruleCode, severity,
                ExceptionStatus.RESOLVED, seededIncidentId, siemForwardedAt,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public AccessException withSeededIncident(UUID incidentId, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new AccessException(id, siteCode, eventId, readerId, zoneCode, ruleCode, severity, status, incidentId,
                siemForwardedAt, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public AccessException withSiemForwarded(Instant forwardedAt, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new AccessException(id, siteCode, eventId, readerId, zoneCode, ruleCode, severity, status,
                seededIncidentId, forwardedAt, metadata.modifiedBy(actorId, at, channel, correlationId));
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
