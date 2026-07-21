package gh.edu.clet.sfl.facilities.maintenance.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WorkOrder(
        UUID id,
        String workOrderNumber,
        UUID facilityFaultId,
        String faultNumber,
        String siteCode,
        String locationCode,
        String title,
        FaultPriority priority,
        WorkOrderStatus status,
        String assignedTo,
        String closureNotes,
        String createdBy,
        Instant createdAt,
        Instant assignedAt,
        Instant closedAt) {

    public WorkOrder {
        Objects.requireNonNull(id, "id is required");
        requireText(workOrderNumber, "workOrderNumber");
        Objects.requireNonNull(facilityFaultId, "facilityFaultId is required");
        requireText(faultNumber, "faultNumber");
        requireText(siteCode, "siteCode");
        requireText(locationCode, "locationCode");
        requireText(title, "title");
        Objects.requireNonNull(priority, "priority is required");
        Objects.requireNonNull(status, "status is required");
        requireText(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt is required");
    }

    public static WorkOrder createFromFault(UUID id, String workOrderNumber, FacilityFault fault, String createdBy,
            Instant createdAt) {
        Objects.requireNonNull(fault, "facilityFault is required");
        return new WorkOrder(id, workOrderNumber, fault.id(), fault.faultNumber(), fault.siteCode(),
                fault.locationCode(), fault.title(), fault.priority(), WorkOrderStatus.OPEN, null, null,
                createdBy, createdAt, null, null);
    }

    public WorkOrder assignTo(String assignee, Instant now) {
        requireText(assignee, "assignedTo");
        if (status == WorkOrderStatus.CLOSED) {
            throw new IllegalStateException("Closed work orders cannot be assigned");
        }
        return new WorkOrder(id, workOrderNumber, facilityFaultId, faultNumber, siteCode, locationCode, title,
                priority, WorkOrderStatus.ASSIGNED, assignee.strip(), closureNotes, createdBy, createdAt, now, closedAt);
    }

    public WorkOrder close(String notes, Instant now) {
        requireText(notes, "closureNotes");
        if (status == WorkOrderStatus.CLOSED) {
            throw new IllegalStateException("Work order is already closed");
        }
        return new WorkOrder(id, workOrderNumber, facilityFaultId, faultNumber, siteCode, locationCode, title,
                priority, WorkOrderStatus.CLOSED, assignedTo, notes.strip(), createdBy, createdAt, assignedAt, now);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}