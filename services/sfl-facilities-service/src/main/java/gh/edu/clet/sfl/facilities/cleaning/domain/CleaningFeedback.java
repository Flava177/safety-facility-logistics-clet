package gh.edu.clet.sfl.facilities.cleaning.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An occupant's rating of a completed clean - SRS-SFL-S169-02 "rating plus optional comment".
 *
 * <p>{@code roomId} and {@code vendorId} are copied from the task when the feedback is written, so
 * "repeated low ratings for the same space or vendor" is a count over this table and never a join
 * whose answer changes when a task is later reassigned.
 *
 * <p>The comment is free text an occupant typed and may name people. It is stored and shown to
 * supervisors; it never travels in an integration event.
 */
public record CleaningFeedback(
        UUID id,
        String siteCode,
        UUID taskId,
        UUID roomId,
        UUID vendorId,
        int rating,
        String comment,
        String submittedBy,
        Instant submittedAt,
        RecordMetadata metadata) {

    public static final int MAX_COMMENT = 1000;

    public CleaningFeedback {
        Objects.requireNonNull(id, "id is required");
        siteCode = EstateCodes.normalize(siteCode);
        Objects.requireNonNull(taskId, "taskId is required");
        Objects.requireNonNull(roomId, "roomId is required");
        if (rating < 1 || rating > 5) {
            throw new FacilitiesException.ValidationFailedException("A cleaning rating is from 1 to 5.");
        }
        comment = EstateCodes.blankToNull(comment);
        if (comment != null && comment.length() > MAX_COMMENT) {
            throw new FacilitiesException.ValidationFailedException(
                    "A feedback comment may be at most " + MAX_COMMENT + " characters.");
        }
        EstateCodes.require(submittedBy, "submittedBy");
        Objects.requireNonNull(submittedAt, "submittedAt is required");
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static CleaningFeedback submit(UUID id, CleaningTask task, int rating, String comment, String actorId,
            Instant at, SourceChannel channel, String correlationId) {
        return new CleaningFeedback(id, task.siteCode(), task.id(), task.roomId(), task.vendorId(), rating, comment,
                actorId, at, RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }
}
