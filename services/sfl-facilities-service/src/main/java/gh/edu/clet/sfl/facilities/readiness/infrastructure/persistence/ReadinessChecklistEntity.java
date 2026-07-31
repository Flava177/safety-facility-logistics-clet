package gh.edu.clet.sfl.facilities.readiness.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessChecklist;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessChecklistItem;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A readiness checklist and its items.
 *
 * <p>The one place in S152 that uses a JPA association rather than an id reference: a checklist's
 * items are a true composition — they have no life without it, are always loaded with it and are
 * replaced wholesale when it changes. {@code orphanRemoval} is what makes "replace the items" a
 * single save rather than a delete-then-insert the caller has to remember.
 */
@Entity
@Table(name = "facility_readiness_checklists", schema = "facilities")
class ReadinessChecklistEntity {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "checklist_code", nullable = false, length = 60)
    private String checklistCode;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(length = 1000)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "space_type", length = 40)
    private SpaceType spaceType;
    @Enumerated(EnumType.STRING)
    @Column(name = "operating_mode", length = 20)
    private OperatingMode operatingMode;
    @Column(nullable = false)
    private int version;
    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private RecordLifecycleStatus lifecycleStatus;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "checklist_id", nullable = false)
    @OrderBy("sortOrder asc")
    private List<ReadinessChecklistItemEntity> items = new ArrayList<>();

    protected ReadinessChecklistEntity() {
    }

    static ReadinessChecklistEntity from(ReadinessChecklist checklist) {
        ReadinessChecklistEntity entity = new ReadinessChecklistEntity();
        entity.apply(checklist);
        return entity;
    }

    /** Copies the aggregate onto this row, replacing the item collection in place. */
    void apply(ReadinessChecklist checklist) {
        id = checklist.id();
        siteCode = checklist.siteCode();
        checklistCode = checklist.checklistCode();
        name = checklist.name();
        description = checklist.description();
        spaceType = checklist.spaceType();
        operatingMode = checklist.operatingMode();
        version = checklist.version();
        lifecycleStatus = checklist.lifecycleStatus();
        metadata = RecordMetadataEmbeddable.from(checklist.metadata());
        // Mutate rather than reassign: a managed collection that is replaced wholesale loses
        // Hibernate's orphan tracking and leaves the old rows behind.
        items.clear();
        checklist.items().stream().map(ReadinessChecklistItemEntity::from).forEach(items::add);
    }

    ReadinessChecklist toDomain() {
        List<ReadinessChecklistItem> domainItems = items.stream()
                .map(item -> item.toDomain(id))
                .toList();
        return new ReadinessChecklist(id, siteCode, checklistCode, name, description, spaceType, operatingMode,
                version, domainItems, lifecycleStatus, metadata.toDomain());
    }
}
