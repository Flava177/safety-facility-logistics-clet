package gh.edu.clet.sfl.facilities.readiness.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSeverity;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessChecklistItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "facility_readiness_checklist_items", schema = "facilities")
class ReadinessChecklistItemEntity {

    @Id
    private UUID id;
    @Column(name = "item_code", nullable = false, length = 60)
    private String itemCode;
    @Column(nullable = false, length = 500)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "severity_if_failed", nullable = false, length = 20)
    private BlockerSeverity severityIfFailed;
    @Column(nullable = false)
    private boolean mandatory;
    @Column(nullable = false)
    private int weight;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ReadinessChecklistItemEntity() {
    }

    static ReadinessChecklistItemEntity from(ReadinessChecklistItem item) {
        ReadinessChecklistItemEntity entity = new ReadinessChecklistItemEntity();
        entity.id = item.id();
        entity.itemCode = item.itemCode();
        entity.description = item.description();
        entity.severityIfFailed = item.severityIfFailed();
        entity.mandatory = item.mandatory();
        entity.weight = item.weight();
        entity.sortOrder = item.sortOrder();
        return entity;
    }

    /**
     * @param checklistId supplied by the owner, because the join column is managed by the parent's
     *        {@code @JoinColumn} and is therefore not a field on this entity
     */
    ReadinessChecklistItem toDomain(UUID checklistId) {
        return new ReadinessChecklistItem(id, checklistId, itemCode, description, severityIfFailed, mandatory,
                weight, sortOrder);
    }
}
