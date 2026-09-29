package gh.edu.clet.sfl.facilities.cleaning.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One checklist line on one task, copied from the template when the task was raised - SRS-SFL-S169-02.
 *
 * <p>Marking an item done without its required photograph is allowed here, deliberately. A cleaner on a
 * phone with no signal marks the floor done and uploads the photo from the corridor; refusing the tick
 * would push them to tick it later from memory. The gate that matters is completion
 * ({@code ChecklistCompletionPolicy}): the task cannot close while a photo-required item has no photo.
 */
public record TaskChecklistItem(
        UUID id,
        String siteCode,
        UUID taskId,
        String itemCode,
        String label,
        int sequence,
        boolean photoRequired,
        boolean done,
        String doneBy,
        Instant doneAt,
        PhotoEvidence photo,
        String notes,
        RecordMetadata metadata) {

    public TaskChecklistItem {
        Objects.requireNonNull(id, "id is required");
        siteCode = EstateCodes.normalize(siteCode);
        Objects.requireNonNull(taskId, "taskId is required");
        EstateCodes.require(itemCode, "itemCode");
        EstateCodes.require(label, "label");
        notes = EstateCodes.blankToNull(notes);
        Objects.requireNonNull(metadata, "metadata is required");
    }

    /** The task's copy of a template item, not yet done. */
    public static TaskChecklistItem fromTemplate(UUID id, CleaningTask task, ChecklistTemplate.Item item,
            String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new TaskChecklistItem(id, task.siteCode(), task.id(), item.itemCode(), item.label(), item.sequence(),
                item.photoRequired(), false, null, null, null, null,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /**
     * Records the item as done, or reopens it. A photo supplied later replaces an earlier one: the most
     * recent reference is the one a reviewer should look at, and the audit trail keeps every version.
     */
    public TaskChecklistItem record(boolean nowDone, PhotoEvidence newPhoto, String newNotes, String actorId,
            Instant at, SourceChannel channel, String correlationId) {
        PhotoEvidence keptPhoto = newPhoto != null ? newPhoto : photo;
        return new TaskChecklistItem(id, siteCode, taskId, itemCode, label, sequence, photoRequired, nowDone,
                nowDone ? actorId : null, nowDone ? at : null, keptPhoto, newNotes == null ? notes : newNotes,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** {@code true} when this item still stands in the way of completion. */
    public boolean unaddressed() {
        return !done || (photoRequired && photo == null);
    }
}
