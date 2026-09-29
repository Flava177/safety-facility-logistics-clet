package gh.edu.clet.sfl.facilities.cleaning.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Repeated low ratings for one space or one vendor, surfaced for supervisor review - SRS-SFL-S169-02.
 *
 * <p>One open flag per subject. A further low rating while the flag is open updates its count rather
 * than raising a second flag, so a supervisor's queue shows one line per problem, not one per complaint.
 * Reviewing closes it with notes; a new run of low ratings after that opens a fresh flag, which is the
 * signal that whatever the supervisor did has not worked.
 */
public record LowRatingFlag(
        UUID id,
        String siteCode,
        FlagSubjectType subjectType,
        UUID subjectId,
        String subjectLabel,
        int lowRatingCount,
        Instant windowStart,
        UUID lastFeedbackId,
        boolean open,
        Instant flaggedAt,
        String reviewedBy,
        Instant reviewedAt,
        String reviewNotes,
        RecordMetadata metadata) {

    public LowRatingFlag {
        Objects.requireNonNull(id, "id is required");
        siteCode = EstateCodes.normalize(siteCode);
        Objects.requireNonNull(subjectType, "subjectType is required");
        Objects.requireNonNull(subjectId, "subjectId is required");
        EstateCodes.require(subjectLabel, "subjectLabel");
        Objects.requireNonNull(windowStart, "windowStart is required");
        Objects.requireNonNull(lastFeedbackId, "lastFeedbackId is required");
        Objects.requireNonNull(flaggedAt, "flaggedAt is required");
        reviewNotes = EstateCodes.blankToNull(reviewNotes);
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static LowRatingFlag raise(UUID id, String siteCode, FlagSubjectType type, UUID subjectId, String label,
            int count, Instant windowStart, UUID feedbackId, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new LowRatingFlag(id, siteCode, type, subjectId, label, count, windowStart, feedbackId, true, at, null,
                null, null, RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** Another low rating while the flag is open. */
    public LowRatingFlag refresh(int count, Instant newWindowStart, UUID feedbackId, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new LowRatingFlag(id, siteCode, subjectType, subjectId, subjectLabel, count, newWindowStart,
                feedbackId, true, flaggedAt, null, null, null, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** A supervisor has looked at it. Notes are required: "reviewed" alone records nothing anybody did. */
    public LowRatingFlag review(String notes, String actorId, Instant at, SourceChannel channel, String correlationId) {
        if (!open) {
            throw new FacilitiesException.InvalidStateTransitionException("This flag has already been reviewed.");
        }
        if (notes == null || notes.isBlank()) {
            throw new FacilitiesException.ValidationFailedException("Review notes are required.");
        }
        return new LowRatingFlag(id, siteCode, subjectType, subjectId, subjectLabel, lowRatingCount, windowStart,
                lastFeedbackId, false, flaggedAt, actorId, at, notes.strip(),
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }
}
