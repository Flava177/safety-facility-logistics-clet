package gh.edu.clet.sfl.safetysecurity.cctv.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * A retention schedule for one camera or zone - SRS-SFL-S161-05: "governs footage retention through
 * the VMS per the configured, legally bounded retention schedule per camera or zone, with scheduled
 * purge and legal-hold override." SFL holds the schedule and the legal-hold flag; the VMS is the one
 * actually purging its own recordings on a normal day - what {@link RetentionPurgeSweepService} purges
 * here is SFL's own evidence-item references once their retention window elapses, unless a legal hold
 * applies, which is exactly the sentence this record's {@link #legalHold} exists to satisfy.
 */
public record RetentionPolicy(UUID id, String siteCode, RetentionScope scope, String scopeRef, int retentionDays,
        boolean legalHold, String legalHoldReason, RecordMetadata metadata) {

    public RetentionPolicy {
        Objects.requireNonNull(id, "id is required");
        require(siteCode, "siteCode");
        Objects.requireNonNull(scope, "scope is required");
        require(scopeRef, "scopeRef");
        if (retentionDays <= 0) {
            throw new IllegalArgumentException("retentionDays must be positive");
        }
        legalHoldReason = blankToNull(legalHoldReason);
        if (legalHold && legalHoldReason == null) {
            throw new IllegalArgumentException("legalHoldReason is required when legalHold is true");
        }
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static RetentionPolicy define(UUID id, String siteCode, RetentionScope scope, String scopeRef,
            int retentionDays, String actorId, java.time.Instant at, SourceChannel channel, String correlationId) {
        return new RetentionPolicy(id, siteCode, scope, scopeRef, retentionDays, false, null,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    public RetentionPolicy placeLegalHold(String reason, String actorId, java.time.Instant at, SourceChannel channel,
            String correlationId) {
        return new RetentionPolicy(id, siteCode, scope, scopeRef, retentionDays, true, reason,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public RetentionPolicy releaseLegalHold(String actorId, java.time.Instant at, SourceChannel channel,
            String correlationId) {
        return new RetentionPolicy(id, siteCode, scope, scopeRef, retentionDays, false, null,
                metadata.modifiedBy(actorId, at, channel, correlationId));
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
