package gh.edu.clet.sfl.facilities.readiness.domain;

import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A reason a space is not ready (SRS-SFL-S152-05, "blockers by severity").
 *
 * <p>The blocker, not the status, is the useful record. A dashboard that says a hall is {@code BLOCKED}
 * tells an operator to go and find out why; a dashboard that lists "fire door will not latch —
 * CRITICAL, raised 2 hours ago" tells them what to do. The status is derived from these.
 *
 * <p>Resolution is recorded rather than deleted: §21.2 requires records used for examination
 * continuity to be protected from deletion, and a blocker that was raised and cleared before an
 * examination is exactly what an after-the-fact review asks about.
 */
public record ReadinessBlocker(
        UUID id,
        UUID roomId,
        String siteCode,
        UUID assessmentId,
        BlockerSource source,
        String sourceReference,
        BlockerSeverity severity,
        String description,
        String raisedBy,
        Instant raisedAt,
        boolean resolved,
        String resolvedBy,
        Instant resolvedAt,
        String resolutionNotes) {

    public ReadinessBlocker {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(roomId, "roomId is required");
        EstateCodes.require(siteCode, "siteCode");
        Objects.requireNonNull(source, "source is required");
        Objects.requireNonNull(severity, "severity is required");
        EstateCodes.require(description, "description");
        EstateCodes.require(raisedBy, "raisedBy");
        Objects.requireNonNull(raisedAt, "raisedAt is required");
    }

    public static ReadinessBlocker raise(UUID roomId, String siteCode, UUID assessmentId, BlockerSource source,
            String sourceReference, BlockerSeverity severity, String description, String actorId, Instant at) {
        return new ReadinessBlocker(UUID.randomUUID(), roomId, EstateCodes.normalize(siteCode), assessmentId,
                source, EstateCodes.blankToNull(sourceReference), severity, description.strip(), actorId, at,
                false, null, null, null);
    }

    /**
     * Closes the blocker.
     *
     * <p>Requires a note. "Required evidence must be attached before closure" is the S152-02 rule for
     * workflow closure, and a blocker cleared with no explanation is the readiness equivalent — it
     * leaves a reviewer unable to tell a fix from a dismissal.
     */
    public ReadinessBlocker resolve(String notes, String actorId, Instant at) {
        if (resolved) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Readiness blocker " + id + " is already resolved.");
        }
        if (notes == null || notes.isBlank()) {
            throw new FacilitiesException.ValidationFailedException(
                    "A resolution note is required when closing a readiness blocker.");
        }
        return new ReadinessBlocker(id, roomId, siteCode, assessmentId, source, sourceReference, severity,
                description, raisedBy, raisedAt, true, actorId, at, notes.strip());
    }

    public boolean isOpen() {
        return !resolved;
    }

    /** {@code true} when this blocker, being open and critical, forbids {@code READY}. */
    public boolean blocksReadiness() {
        return isOpen() && severity.blocksReadiness();
    }

    /** How long this blocker has been open, as of {@code now}. */
    public java.time.Duration ageAt(Instant now) {
        return java.time.Duration.between(raisedAt, resolved && resolvedAt != null ? resolvedAt : now);
    }
}
