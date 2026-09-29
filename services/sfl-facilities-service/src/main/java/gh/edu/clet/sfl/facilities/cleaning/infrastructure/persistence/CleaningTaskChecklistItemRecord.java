package gh.edu.clet.sfl.facilities.cleaning.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.cleaning.domain.PhotoEvidence;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskChecklistItem;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link TaskChecklistItem}. The photo is a reference and a hash; there is no binary column. */
@Entity
@Table(name = "cleaning_task_checklist_items", schema = "facilities")
public class CleaningTaskChecklistItemRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "task_id", nullable = false)
    private UUID taskId;
    @Column(name = "item_code", nullable = false, length = 60)
    private String itemCode;
    @Column(nullable = false, length = 300)
    private String label;
    @Column(name = "sequence_no", nullable = false)
    private int sequence;
    @Column(name = "photo_required", nullable = false)
    private boolean photoRequired;
    @Column(nullable = false)
    private boolean done;
    @Column(name = "done_by", length = 160)
    private String doneBy;
    @Column(name = "done_at")
    private Instant doneAt;
    @Column(name = "photo_reference", length = 500)
    private String photoReference;
    @Column(name = "photo_content_hash", length = 64)
    private String photoContentHash;
    @Column(length = 1000)
    private String notes;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected CleaningTaskChecklistItemRecord() {
    }

    static CleaningTaskChecklistItemRecord empty() {
        return new CleaningTaskChecklistItemRecord();
    }

    public void apply(TaskChecklistItem item) {
        id = item.id();
        siteCode = item.siteCode();
        taskId = item.taskId();
        itemCode = item.itemCode();
        label = item.label();
        sequence = item.sequence();
        photoRequired = item.photoRequired();
        done = item.done();
        doneBy = item.doneBy();
        doneAt = item.doneAt();
        photoReference = item.photo() == null ? null : item.photo().reference();
        photoContentHash = item.photo() == null ? null : item.photo().contentHash();
        notes = item.notes();
        metadata = RecordMetadataEmbeddable.from(item.metadata());
    }

    public TaskChecklistItem toDomain() {
        return new TaskChecklistItem(id, siteCode, taskId, itemCode, label, sequence, photoRequired, done, doneBy,
                doneAt, PhotoEvidence.ofNullable(photoReference, photoContentHash), notes,
                metadata.toDomain(recordVersion()));
    }
}
