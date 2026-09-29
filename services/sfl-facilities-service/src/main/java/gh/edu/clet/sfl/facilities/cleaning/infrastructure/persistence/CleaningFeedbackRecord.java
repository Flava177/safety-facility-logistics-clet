package gh.edu.clet.sfl.facilities.cleaning.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningFeedback;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link CleaningFeedback}. */
@Entity
@Table(name = "cleaning_feedback", schema = "facilities")
public class CleaningFeedbackRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "task_id", nullable = false)
    private UUID taskId;
    @Column(name = "room_id", nullable = false)
    private UUID roomId;
    @Column(name = "vendor_id")
    private UUID vendorId;
    @Column(nullable = false)
    private int rating;
    @Column(length = 1000)
    private String comment;
    @Column(name = "submitted_by", nullable = false, length = 160)
    private String submittedBy;
    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected CleaningFeedbackRecord() {
    }

    static CleaningFeedbackRecord empty() {
        return new CleaningFeedbackRecord();
    }

    public void apply(CleaningFeedback feedback) {
        id = feedback.id();
        siteCode = feedback.siteCode();
        taskId = feedback.taskId();
        roomId = feedback.roomId();
        vendorId = feedback.vendorId();
        rating = feedback.rating();
        comment = feedback.comment();
        submittedBy = feedback.submittedBy();
        submittedAt = feedback.submittedAt();
        metadata = RecordMetadataEmbeddable.from(feedback.metadata());
    }

    public CleaningFeedback toDomain() {
        return new CleaningFeedback(id, siteCode, taskId, roomId, vendorId, rating, comment, submittedBy, submittedAt,
                metadata.toDomain(recordVersion()));
    }
}
