package gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderPart;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link WorkOrderPart}. */
@Entity
@Table(name = "work_order_parts", schema = "facilities")
public class WorkOrderPartRecord {

    @Id
    private UUID id;
    @Column(name = "work_order_id", nullable = false)
    private UUID workOrderId;
    @Column(name = "part_code", nullable = false, length = 80)
    private String partCode;
    @Column(nullable = false, length = 400)
    private String description;
    @Column(nullable = false)
    private int quantity;
    @Column(name = "unit_cost", precision = 14, scale = 2)
    private BigDecimal unitCost;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(length = 200)
    private String supplier;
    @Column(name = "recorded_by", nullable = false, length = 160)
    private String recordedBy;
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected WorkOrderPartRecord() {
    }

    public static WorkOrderPartRecord from(WorkOrderPart part) {
        WorkOrderPartRecord record = new WorkOrderPartRecord();
        record.apply(part);
        return record;
    }

    public void apply(WorkOrderPart part) {
        id = part.id();
        workOrderId = part.workOrderId();
        partCode = part.partCode();
        description = part.description();
        quantity = part.quantity();
        unitCost = part.unitCost();
        currency = part.currency();
        supplier = part.supplier();
        recordedBy = part.recordedBy();
        recordedAt = part.recordedAt();
    }

    public WorkOrderPart toDomain() {
        return new WorkOrderPart(id, workOrderId, partCode, description, quantity, unitCost, currency,
                supplier, recordedBy, recordedAt);
    }
}
