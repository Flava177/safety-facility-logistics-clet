package gh.edu.clet.sfl.facilities.cleaning.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.cleaning.domain.ChecklistTemplate;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;

/** JPA mapping for the header of a {@link ChecklistTemplate}; its items are {@link CleaningChecklistTemplateItemRecord}. */
@Entity
@Table(name = "cleaning_checklist_templates", schema = "facilities")
public class CleaningChecklistTemplateRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Enumerated(EnumType.STRING)
    @Column(name = "space_type", nullable = false, length = 40)
    private SpaceType spaceType;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(name = "template_version", nullable = false)
    private int templateVersion;
    @Column(nullable = false)
    private boolean active;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected CleaningChecklistTemplateRecord() {
    }

    static CleaningChecklistTemplateRecord empty() {
        return new CleaningChecklistTemplateRecord();
    }

    public void apply(ChecklistTemplate template) {
        id = template.id();
        siteCode = template.siteCode();
        spaceType = template.spaceType();
        name = template.name();
        templateVersion = template.version();
        active = template.active();
        metadata = RecordMetadataEmbeddable.from(template.metadata());
    }

    public ChecklistTemplate toDomain(List<ChecklistTemplate.Item> items) {
        return new ChecklistTemplate(id, siteCode, spaceType, name, templateVersion, active, items,
                metadata.toDomain(recordVersion()));
    }

    public UUID getId() {
        return id;
    }
}
