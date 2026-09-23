package gh.edu.clet.sfl.safetysecurity.intrusion.domain.model;

import gh.edu.clet.sfl.safetysecurity.intrusion.domain.exception.IntrusionErrorCode;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.exception.IntrusionException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A time-bound, authorised disarm of a protected zone - SRS-SFL-S162-04: "manual disarm requires an
 * authorised, time-bound reason and reverts to armed automatically at expiry". Mirrors
 * {@code accesscontrol.domain.model.AccessOverride}'s shape, without the break-glass path: S162's own
 * requirement text names only the authorised-reason-and-expiry case, not a pre-authorised emergency
 * exception, so that variant is not built here.
 */
public record DisarmOverride(UUID id, String siteCode, String zoneCode, String reason, String requestedBy,
        String approverId, Instant startsAt, Instant expiresAt, DisarmOverrideStatus status,
        RecordMetadata metadata) {

    public DisarmOverride {
        Objects.requireNonNull(id, "id is required");
        require(siteCode, "siteCode");
        require(zoneCode, "zoneCode");
        require(reason, "reason");
        require(requestedBy, "requestedBy");
        require(approverId, "approverId");
        Objects.requireNonNull(startsAt, "startsAt is required");
        Objects.requireNonNull(expiresAt, "expiresAt is required");
        if (!expiresAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("expiresAt must be after startsAt");
        }
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static DisarmOverride request(UUID id, String siteCode, String zoneCode, String reason,
            String requestedBy, String approverId, Instant startsAt, Instant expiresAt, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new DisarmOverride(id, siteCode, zoneCode, reason, requestedBy, approverId, startsAt, expiresAt,
                DisarmOverrideStatus.ACTIVE, RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** Automatic reversion at expiry - reached only from the expiry sweep. */
    public DisarmOverride expire(Instant at, SourceChannel channel, String correlationId) {
        requireOpen();
        return new DisarmOverride(id, siteCode, zoneCode, reason, requestedBy, approverId, startsAt, expiresAt,
                DisarmOverrideStatus.EXPIRED, metadata.modifiedBy("SYSTEM-EXPIRY-SWEEP", at, channel, correlationId));
    }

    public DisarmOverride revoke(String actorId, Instant at, SourceChannel channel, String correlationId) {
        requireOpen();
        return new DisarmOverride(id, siteCode, zoneCode, reason, requestedBy, approverId, startsAt, expiresAt,
                DisarmOverrideStatus.REVOKED, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public boolean isEffective(Instant now) {
        return status == DisarmOverrideStatus.ACTIVE && !now.isBefore(startsAt) && now.isBefore(expiresAt);
    }

    private void requireOpen() {
        if (status != DisarmOverrideStatus.ACTIVE) {
            throw IntrusionException.of(IntrusionErrorCode.INTRUSION_ZONE_DISARM_ALREADY_CLOSED);
        }
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
