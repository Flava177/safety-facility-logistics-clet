package gh.edu.clet.sfl.facilities.eventlogistics.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventResourceType;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventTemplateLine;
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

/** JPA mapping for {@link EventTemplateLine}. Column names match V20 exactly. */
@Entity
@Table(name = "event_template_lines", schema = "facilities")
public class EventTemplateLineRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "event_category", nullable = false, length = 60)
    private String eventCategory;
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 30)
    private EventResourceType resourceType;
    @Column(nullable = false, length = 1000)
    private String description;
    @Column(nullable = false)
    private int quantity;
    @Column(name = "bookable_resource_id")
    private UUID bookableResourceId;
    @Column(nullable = false, length = 2000)
    private String lesson;
    @Column(name = "gap_count", nullable = false)
    private int gapCount;
    @Column(name = "last_gap_at", nullable = false)
    private Instant lastGapAt;
    @Column(name = "last_gap_task_id")
    private UUID lastGapTaskId;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected EventTemplateLineRecord() {
    }

    static EventTemplateLineRecord empty() {
        return new EventTemplateLineRecord();
    }

    void apply(EventTemplateLine line) {
        id = line.id();
        siteCode = line.siteCode();
        eventCategory = line.eventCategory();
        resourceType = line.resourceType();
        description = EventResourceRequestRecord.truncate(line.description(), 1000);
        quantity = line.quantity();
        bookableResourceId = line.bookableResourceId();
        lesson = EventResourceRequestRecord.truncate(line.lesson(), 2000);
        gapCount = line.gapCount();
        lastGapAt = line.lastGapAt();
        lastGapTaskId = line.lastGapTaskId();
        metadata = RecordMetadataEmbeddable.from(line.metadata());
    }

    EventTemplateLine toDomain() {
        return new EventTemplateLine(id, siteCode, eventCategory, resourceType, description, quantity,
                bookableResourceId, lesson, gapCount, lastGapAt, lastGapTaskId, metadata.toDomain(recordVersion()));
    }
}
