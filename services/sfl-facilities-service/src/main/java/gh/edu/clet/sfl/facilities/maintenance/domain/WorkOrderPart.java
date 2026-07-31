package gh.edu.clet.sfl.facilities.maintenance.domain;

import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A part consumed on a work order.
 *
 * <p>SRS-SFL-S153 names parts in the module's purpose. This is deliberately the smallest thing that
 * satisfies it: what was fitted, how many, what it cost and who recorded it. It is <strong>not</strong>
 * a stores system — there is no stock level, no reorder point and no reservation, because CLET has
 * no inventory system for this to reconcile against and inventing one here would produce numbers
 * nobody maintains.
 *
 * <p>Cost is nullable for the same reason. A technician fitting a part from the van often does not
 * know its price, and a mandatory field they cannot answer is a field that gets a zero typed into it.
 */
public record WorkOrderPart(
        UUID id,
        UUID workOrderId,
        String partCode,
        String description,
        int quantity,
        BigDecimal unitCost,
        String currency,
        String supplier,
        String recordedBy,
        Instant recordedAt) {

    public WorkOrderPart {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(workOrderId, "workOrderId is required");
        partCode = EstateCodes.normalize(partCode);
        EstateCodes.require(description, "description");
        description = description.strip();
        supplier = EstateCodes.blankToNull(supplier);
        currency = currency == null || currency.isBlank() ? "GHS" : currency.strip().toUpperCase(java.util.Locale.ROOT);
        EstateCodes.require(recordedBy, "recordedBy");
        recordedBy = recordedBy.strip();
        Objects.requireNonNull(recordedAt, "recordedAt is required");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
        if (unitCost != null && unitCost.signum() < 0) {
            throw new IllegalArgumentException("unitCost cannot be negative");
        }
    }

    public static WorkOrderPart record(UUID id, UUID workOrderId, String partCode, String description,
            int quantity, BigDecimal unitCost, String currency, String supplier, String recordedBy,
            Instant recordedAt) {
        return new WorkOrderPart(id, workOrderId, partCode, description, quantity, unitCost, currency, supplier,
                recordedBy, recordedAt);
    }

    /** Line total, or {@code null} when no unit cost was recorded. */
    public BigDecimal lineCost() {
        return unitCost == null ? null : unitCost.multiply(BigDecimal.valueOf(quantity));
    }
}
