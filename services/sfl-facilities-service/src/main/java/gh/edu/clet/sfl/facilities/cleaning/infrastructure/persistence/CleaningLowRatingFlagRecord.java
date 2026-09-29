package gh.edu.clet.sfl.facilities.cleaning.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.cleaning.domain.FlagSubjectType;
import gh.edu.clet.sfl.facilities.cleaning.domain.LowRatingFlag;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link LowRatingFlag}. {@code status} is OPEN or REVIEWED, the domain's {@code open}. */
@Entity
@Table(name = "cleaning_low_rating_flags", schema = "facilities")
public class CleaningLowRatingFlagRecord extends VersionedRecord {

    static final String OPEN = "OPEN";
    static final String REVIEWED = "REVIEWED";

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 20)
    private FlagSubjectType subjectType;
    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;
    @Column(name = "subject_label", nullable = false, length = 200)
    private String subjectLabel;
    @Column(name = "low_rating_count", nullable = false)
    private int lowRatingCount;
    @Column(name = "window_start", nullable = false)
    private Instant windowStart;
    @Column(name = "last_feedback_id", nullable = false)
    private UUID lastFeedbackId;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "flagged_at", nullable = false)
    private Instant flaggedAt;
    @Column(name = "reviewed_by", length = 160)
    private String reviewedBy;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    @Column(name = "review_notes", length = 2000)
    private String reviewNotes;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected CleaningLowRatingFlagRecord() {
    }

    static CleaningLowRatingFlagRecord empty() {
        return new CleaningLowRatingFlagRecord();
    }

    public void apply(LowRatingFlag flag) {
        id = flag.id();
        siteCode = flag.siteCode();
        subjectType = flag.subjectType();
        subjectId = flag.subjectId();
        subjectLabel = flag.subjectLabel();
        lowRatingCount = flag.lowRatingCount();
        windowStart = flag.windowStart();
        lastFeedbackId = flag.lastFeedbackId();
        status = flag.open() ? OPEN : REVIEWED;
        flaggedAt = flag.flaggedAt();
        reviewedBy = flag.reviewedBy();
        reviewedAt = flag.reviewedAt();
        reviewNotes = flag.reviewNotes();
        metadata = RecordMetadataEmbeddable.from(flag.metadata());
    }

    public LowRatingFlag toDomain() {
        return new LowRatingFlag(id, siteCode, subjectType, subjectId, subjectLabel, lowRatingCount, windowStart,
                lastFeedbackId, OPEN.equals(status), flaggedAt, reviewedBy, reviewedAt, reviewNotes,
                metadata.toDomain(recordVersion()));
    }
}
