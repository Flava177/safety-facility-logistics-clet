package gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model;

import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.exception.AccessControlErrorCode;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.exception.AccessControlException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A time-bound access override - SRS-SFL-S160a-03: temporary grant, door release or zone opening,
 * always with a reason and an explicit expiry. A normal override requires an approver at creation
 * (the actor authorising it, gated by {@code ACCESS_OVERRIDE_CREATE}); a {@link #breakGlass}
 * override may be raised by a pre-authorised role during a declared emergency with no approver yet,
 * and is approved after the fact via {@link #recordPostHocApproval}.
 */
public record AccessOverride(UUID id, String siteCode, String scopeRef, String reason, String requestedBy,
        String approverId, boolean breakGlass, Instant startsAt, Instant expiresAt, OverrideStatus status,
        RecordMetadata metadata) {

    public AccessOverride {
        Objects.requireNonNull(id, "id is required");
        require(siteCode, "siteCode");
        require(scopeRef, "scopeRef");
        require(reason, "reason");
        require(requestedBy, "requestedBy");
        approverId = blankToNull(approverId);
        Objects.requireNonNull(startsAt, "startsAt is required");
        Objects.requireNonNull(expiresAt, "expiresAt is required");
        if (!expiresAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("expiresAt must be after startsAt");
        }
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(metadata, "metadata is required");
        if (!breakGlass && approverId == null) {
            throw new AccessControlException(AccessControlErrorCode.ACCESS_OVERRIDE_NOT_AUTHORISED);
        }
    }

    public static AccessOverride request(UUID id, String siteCode, String scopeRef, String reason,
            String requestedBy, String approverId, Instant startsAt, Instant expiresAt, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new AccessOverride(id, siteCode, scopeRef, reason, requestedBy, approverId, false, startsAt, expiresAt,
                OverrideStatus.ACTIVE, RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** SRS-SFL-S160a-03: "may follow the break-glass principle ... with approval recorded after the fact". */
    public static AccessOverride breakGlass(UUID id, String siteCode, String scopeRef, String reason,
            String requestedBy, Instant startsAt, Instant expiresAt, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new AccessOverride(id, siteCode, scopeRef, reason, requestedBy, null, true, startsAt, expiresAt,
                OverrideStatus.ACTIVE, RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    public AccessOverride recordPostHocApproval(String approverId, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        if (!breakGlass) {
            throw AccessControlException.of(AccessControlErrorCode.ACCESS_CONTROL_INVALID_STATE_TRANSITION);
        }
        return new AccessOverride(id, siteCode, scopeRef, reason, requestedBy, requireApprover(approverId),
                breakGlass, startsAt, expiresAt, status, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** Automatic reversion at expiry (S160a-03) - reached only from the expiry sweep, mirroring
     * {@code VisitorVisit.markNoShow}'s "observation, not something a person asserts" reasoning. */
    public AccessOverride expire(Instant at, SourceChannel channel, String correlationId) {
        requireOpen();
        return new AccessOverride(id, siteCode, scopeRef, reason, requestedBy, approverId, breakGlass, startsAt,
                expiresAt, OverrideStatus.EXPIRED, metadata.modifiedBy("SYSTEM-EXPIRY-SWEEP", at, channel,
                        correlationId));
    }

    public AccessOverride revoke(String actorId, Instant at, SourceChannel channel, String correlationId) {
        requireOpen();
        return new AccessOverride(id, siteCode, scopeRef, reason, requestedBy, approverId, breakGlass, startsAt,
                expiresAt, OverrideStatus.REVOKED, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public boolean isEffective(Instant now) {
        return status == OverrideStatus.ACTIVE && !now.isBefore(startsAt) && now.isBefore(expiresAt);
    }

    private void requireOpen() {
        if (status != OverrideStatus.ACTIVE) {
            throw AccessControlException.of(AccessControlErrorCode.ACCESS_OVERRIDE_ALREADY_CLOSED);
        }
    }

    private static String requireApprover(String approverId) {
        if (approverId == null || approverId.isBlank()) {
            throw AccessControlException.of(AccessControlErrorCode.ACCESS_OVERRIDE_NOT_AUTHORISED);
        }
        return approverId.strip();
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
