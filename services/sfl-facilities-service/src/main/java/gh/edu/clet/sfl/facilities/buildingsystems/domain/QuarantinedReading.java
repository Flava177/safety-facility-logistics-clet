package gh.edu.clet.sfl.facilities.buildingsystems.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An authenticated reading held for review instead of being stored as fact - SRS-SFL-S156-01, -04.
 *
 * <p>"Quarantined for review rather than silently dropped": the vendor's measurement is kept whole, with
 * why it could not be believed, so a reviewer can fix the mapping and release it or discard it with a
 * reason. Nothing here is ever evaluated against a threshold rule or offered to S157, which is what makes
 * the accepted stream safe to treat as fact.
 *
 * <p>Only an <em>authenticated</em> reading reaches quarantine. A forged or malformed message is rejected
 * by the verifier before any of this, and leaves no quarantine row either - quarantine is a review queue
 * for real data, not a place an attacker can fill.
 */
public record QuarantinedReading(
        UUID id,
        String siteCode,
        String sourceId,
        String format,
        String idempotencyKey,
        int itemIndex,
        UUID inboxId,
        String deviceCode,
        String channel,
        MeasuredQuantity quantity,
        BigDecimal value,
        Instant observedAt,
        Instant receivedAt,
        QuarantineReason reason,
        String detail,
        UUID deviceId,
        QuarantineStatus status,
        String resolvedBy,
        Instant resolvedAt,
        String resolutionNote,
        UUID releasedReadingId,
        RecordMetadata metadata) {

    public QuarantinedReading {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(siteCode, "siteCode is required");
        Objects.requireNonNull(reason, "reason is required");
        status = status == null ? QuarantineStatus.PENDING : status;
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public boolean isPending() {
        return status == QuarantineStatus.PENDING;
    }

    /** Stored as fact after the mapping was fixed. Only a filing problem can be released - see {@link QuarantineReason}. */
    public QuarantinedReading release(UUID readingId, String note, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        requirePending();
        if (!reason.releasable()) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "A reading quarantined as " + reason + " is not trusted as fact and can only be discarded with a reason");
        }
        return resolved(QuarantineStatus.RELEASED, readingId, EstateCodes.blankToNull(note), actorId, at, channel,
                correlationId);
    }

    public QuarantinedReading discard(String note, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        requirePending();
        EstateCodes.require(note, "reason");
        return resolved(QuarantineStatus.DISCARDED, null, note.strip(), actorId, at, channel, correlationId);
    }

    private QuarantinedReading resolved(QuarantineStatus target, UUID readingId, String note, String actorId,
            Instant at, SourceChannel channel, String correlationId) {
        return new QuarantinedReading(id, siteCode, sourceId, format, idempotencyKey, itemIndex, inboxId, deviceCode,
                this.channel, quantity, value, observedAt, receivedAt, reason, detail, deviceId, target, actorId, at,
                note, readingId, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    private void requirePending() {
        if (!isPending()) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Quarantined reading " + id + " was already " + status);
        }
    }
}
