package gh.edu.clet.sfl.facilities.cleaning.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.cleaning.domain.ChecklistTemplate;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * One item of a checklist template. Written once with its template and never edited - a changed
 * checklist is a new template version - so its provenance is the template's.
 */
@Entity
@Table(name = "cleaning_checklist_template_items", schema = "facilities")
public class CleaningChecklistTemplateItemRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "template_id", nullable = false)
    private UUID templateId;
    @Column(name = "item_code", nullable = false, length = 60)
    private String itemCode;
    @Column(nullable = false, length = 300)
    private String label;
    @Column(name = "sequence_no", nullable = false)
    private int sequence;
    @Column(name = "photo_required", nullable = false)
    private boolean photoRequired;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected CleaningChecklistTemplateItemRecord() {
    }

    static CleaningChecklistTemplateItemRecord of(ChecklistTemplate template, ChecklistTemplate.Item item,
            RecordMetadata provenance) {
        CleaningChecklistTemplateItemRecord record = new CleaningChecklistTemplateItemRecord();
        record.id = item.id();
        record.siteCode = template.siteCode();
        record.templateId = template.id();
        record.itemCode = item.itemCode();
        record.label = item.label();
        record.sequence = item.sequence();
        record.photoRequired = item.photoRequired();
        record.metadata = RecordMetadataEmbeddable.from(provenance);
        return record;
    }

    public ChecklistTemplate.Item toDomain() {
        return new ChecklistTemplate.Item(id, itemCode, label, sequence, photoRequired);
    }
}
